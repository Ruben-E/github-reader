import java.time.LocalDateTime

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server._
import akka.stream.ActorMaterializer
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import nl.rubenernst.mrdeveloper.scala.api.{ApiContext, ApiSchema}
import nl.rubenernst.mrdeveloper.scala.service.{GithubReaderActor, Read, RepositoryProcessor}
import sangria.ast.Document
import sangria.execution.deferred.DeferredResolver
import sangria.parser.QueryParser
import sangria.execution.{ErrorWithResolver, Executor, QueryAnalysisError}
import sangria.marshalling.sprayJson._
import spray.json._

import scala.util.{Failure, Success}

object Main extends App {
  implicit val system = ActorSystem("GithubReader")
  implicit val materializer = ActorMaterializer()

  import system.dispatcher

  val log = akka.event.Logging.getLogger(system, this)
  log.info(s"Started!")

  val stateActor = system.actorOf(Props[RepositoryProcessor])
  val readerActor = system.actorOf(Props(new GithubReaderActor(stateActor)))
  readerActor ! Read(LocalDateTime.now)

  Http().bindAndHandle(route, "0.0.0.0", sys.props.get("http.port").fold(8080)(_.toInt))

  def executeGraphQLQuery(query: Document, op: Option[String], vars: JsObject) =
    Executor.execute(ApiSchema.ApiSchema, query, new ApiContext(stateActor), variables = vars, operationName = op)
      .map(OK → _)
      .recover {
        case error: QueryAnalysisError ⇒ BadRequest → error.resolveError
        case error: ErrorWithResolver ⇒ InternalServerError → error.resolveError
      }

  def route: Route =
    (post & path("graphql")) {
      entity(as[JsValue]) { requestJson ⇒
        val JsObject(fields) = requestJson
        val JsString(query) = fields("query")

        val operation = fields.get("operationName") collect {
          case JsString(op) ⇒ op
        }

        val vars = fields.get("variables") match {
          case Some(obj: JsObject) ⇒ obj
          case _ ⇒ JsObject.empty
        }

        QueryParser.parse(query) match {
          case Success(queryAst) => complete(executeGraphQLQuery(queryAst, operation, vars))
          case Failure(error) => complete(BadRequest, JsObject("error" → JsString(error.getMessage)))
        }
      }
    } ~
      get {
        getFromResource("graphiql.html")
      }

}
