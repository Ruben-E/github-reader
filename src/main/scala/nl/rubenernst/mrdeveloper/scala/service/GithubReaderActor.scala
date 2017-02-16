package nl.rubenernst.mrdeveloper.scala.service

import java.nio.charset.Charset
import java.time.LocalDateTime

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.event.LoggingReceive
import akka.http.scaladsl.Http
import akka.http.scaladsl.coding.Gzip
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.stream.scaladsl.{Framing, Sink}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import akka.util.ByteString
import nl.rubenernst.mrdeveloper.scala.protocol.RepositoryProtocol.{ErrorMessage, NewRepository, StarRepository}
import spray.json._

/**
  * Created by rubenernst on 19/12/2016.
  */
class GithubReaderActor(stateActor: ActorRef) extends Actor with ActorLogging {

  import akka.pattern.pipe
  import context.dispatcher

  final implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(context.system))

  def receive: Receive = LoggingReceive {
    case Read(dateTime) =>
      val http = Http(context.system)
//      http.singleRequest(HttpRequest(uri = "http://data.githubarchive.org/2016-12-19-10.json.gz")).pipeTo(self)
    //      http.singleRequest(HttpRequest(uri = "http://data.githubarchive.org/2016-12-19-11.json.gz")).pipeTo(self)
    //      http.singleRequest(HttpRequest(uri = "http://data.githubarchive.org/2016-12-19-12.json.gz")).pipeTo(self)
    //      http.singleRequest(HttpRequest(uri = "http://data.githubarchive.org/2016-12-19-13.json.gz")).pipeTo(self)
    //      http.singleRequest(HttpRequest(uri = "http://data.githubarchive.org/2016-12-19-14.json.gz")).pipeTo(self)
    //      http.singleRequest(HttpRequest(uri = "http://data.githubarchive.org/2016-12-19-15.json.gz")).pipeTo(self)
    //      http.singleRequest(HttpRequest(uri = "http://data.githubarchive.org/2016-12-19-16.json.gz")).pipeTo(self)
    case errorMessage: ErrorMessage => log.warning(s"received ErrorMessage: $errorMessage")
    case HttpResponse(StatusCodes.OK, headers, entity, _) =>
      entity.dataBytes
        .via(Gzip.decoderFlow)
        //        .runWith(Sink.head)
        //        .onComplete(response =>
        //          Source.single(response.get)
        .via(Framing.delimiter(ByteString("\n"), Int.MaxValue))
        .map(_.decodeString(Charset.defaultCharset()))
        .map(_.parseJson)
        .mapConcat(json => {
          json.asJsObject.getFields("type", "repo", "payload") match {
            case Seq(JsString("CreateEvent"), repositoryObject: JsObject, payloadObject: JsObject) =>
              val repositoryFields = repositoryObject.getFields("id", "name")
              val payloadFields = payloadObject.getFields("ref_type", "description")

              (repositoryFields, payloadFields) match {
                case (Seq(JsNumber(id), JsString(name)), Seq(JsString("repository"), description)) =>
                  description match {
                    case JsString(d) => List(NewRepository(id.toLong, name, Some(d)))
                    case _ => List(NewRepository(id.toLong, name, None))
                  }

                case _ => List()
              }

            case Seq(JsString("WatchEvent"), repositoryObject: JsObject, payloadObject: JsObject) =>
              val repositoryFields = repositoryObject.getFields("id", "name")

              repositoryFields match {
                case Seq(JsNumber(id), JsString(name)) => List(StarRepository(id.toLong, name))
                case _ => List()
              }
            case _ => List()
          }
        })
        //        .mapConcat(jsObject => {
        //          try {
        //            val typeString = jsObject.fields.get("type")
        //            typeString match {
        //              case Some(JsString("WatchEvent")) => List(WatchedEvent(jsObject))
        //              case Some(JsString("CreateEvent")) =>
        //                val payloadOptional = jsObject.fields.get("payload")
        //                payloadOptional match {
        //                  case Some(payload) =>
        //                    payload.asJsObject.fields
        //                  case None => List()
        //                }
        //                List(RepositoryCreatedEvent(jsObject))
        //              case Some(JsString(e)) => println(e); List()
        //              case _ => List()
        //            }
        //          } catch {
        //            case e: Exception =>
        //              log.warning(s"Got Exception while parsing JsObject [$jsObject]", e)
        //              List()
        //          }
        //        })
        .runWith(Sink.foreach(stateActor ! _))
    //            .runWith(Sink.fold[List[WatchedEvent], WatchedEvent](List())((list, event) => event :: list))
    //        )
    case HttpResponse(code, _, _, _) =>
      log.info("Request failed, response code: " + code)
    case HttpResponse =>
      log.info("Request failed")
  }
}

case class Read(dateTime: LocalDateTime)