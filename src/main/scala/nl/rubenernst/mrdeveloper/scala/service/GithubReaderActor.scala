package nl.rubenernst.mrdeveloper.scala.service

import java.nio.charset.Charset
import java.time.format.DateTimeFormatter
import java.time.{LocalDate, LocalDateTime}

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.event.LoggingReceive
import akka.http.scaladsl.Http
import akka.http.scaladsl.coding.Gzip
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.stream.scaladsl.{Framing, Sink}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import akka.util.ByteString
import nl.rubenernst.mrdeveloper.scala.protocol.RepositoryProtocol.{ErrorMessage, NewRepository, RepositoryCommand, StarRepository}
import spray.json._

import scala.collection.mutable

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
      http.singleRequest(HttpRequest(uri = "http://data.githubarchive.org/2016-12-19-10.json.gz")).pipeTo(self)
//          http.singleRequest(HttpRequest(uri = "http://data.githubarchive.org/2016-12-19-11.json.gz")).pipeTo(self)
//          http.singleRequest(HttpRequest(uri = "http://data.githubarchive.org/2016-12-19-12.json.gz")).pipeTo(self)
//          http.singleRequest(HttpRequest(uri = "http://data.githubarchive.org/2016-12-19-13.json.gz")).pipeTo(self)
//          http.singleRequest(HttpRequest(uri = "http://data.githubarchive.org/2016-12-19-14.json.gz")).pipeTo(self)
//          http.singleRequest(HttpRequest(uri = "http://data.githubarchive.org/2016-12-19-15.json.gz")).pipeTo(self)
//          http.singleRequest(HttpRequest(uri = "http://data.githubarchive.org/2016-12-19-16.json.gz")).pipeTo(self)
    case errorMessage: ErrorMessage => log.warning(s"received ErrorMessage: $errorMessage")
    case HttpResponse(StatusCodes.OK, headers, entity, _) =>
      val date = headers.find(httpHeader => httpHeader.is("last-modified")).map(httpHeader => httpHeader.value()).map(date => LocalDate.parse(date, DateTimeFormatter.RFC_1123_DATE_TIME))
      entity.dataBytes
        .via(Gzip.decoderFlow)
        .via(Framing.delimiter(ByteString("\n"), Int.MaxValue))
        .map(_.decodeString(Charset.defaultCharset()))
        .map(_.parseJson)
//        .sliding(100)
//        .mapConcat(seq => {
        .mapConcat(json => {
          var events = List.empty[RepositoryCommand]
//          seq.foreach(json => {
            val event = json.asJsObject.getFields("type", "repo", "payload") match {
              case Seq(JsString("CreateEvent"), repositoryObject: JsObject, payloadObject: JsObject) => createEvent(repositoryObject, payloadObject)
              case Seq(JsString("WatchEvent"), repositoryObject: JsObject, payloadObject: JsObject) => watchEvent(repositoryObject, payloadObject, date)
              case _ => List.empty
            }
            events = events ::: event
//          })
          events
        })
        .runWith(Sink.foreach(stateActor ! _))
    case HttpResponse(code, _, _, _) =>
      log.info("Request failed, response code: " + code)
    case HttpResponse =>
      log.info("Request failed")
  }

  def watchEvent(repositoryObject: JsObject, payloadObject: JsObject, date: Option[LocalDate]): List[StarRepository] = {
    val repositoryFields = repositoryObject.getFields("id", "name")

    repositoryFields match {
      case Seq(JsNumber(id), JsString(name)) => List(StarRepository(id.toLong, name, 1, date.getOrElse(LocalDate.now())))
      case _ => List.empty
    }
  }

  def createEvent(repositoryObject: JsObject, payloadObject: JsObject): List[NewRepository] = {
    val repositoryFields = repositoryObject.getFields("id", "name")
    val payloadFields = payloadObject.getFields("ref_type", "description")

    (repositoryFields, payloadFields) match {
      case (Seq(JsNumber(id), JsString(name)), Seq(JsString("repository"), description)) =>
        description match {
          case JsString(d) => List(NewRepository(id.toLong, name, Some(d)))
          case _ => List(NewRepository(id.toLong, name, None))
        }

      case _ => List.empty
    }
  }
}

case class Read(dateTime: LocalDateTime)