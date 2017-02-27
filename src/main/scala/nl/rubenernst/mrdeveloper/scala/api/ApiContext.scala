package nl.rubenernst.mrdeveloper.scala.api

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout
import nl.rubenernst.mrdeveloper.scala.protocol.RepositoryProtocol.GetAllRepositories
import nl.rubenernst.mrdeveloper.scala.service.Repository

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import scala.concurrent.duration._

class ApiContext(stateActor: ActorRef) {
  implicit val timeout: Timeout = 10 seconds
  implicit val executionContext: ExecutionContextExecutor = ExecutionContext.global

  def getRepositories: Future[List[Repository]] = {
    val response = stateActor ? GetAllRepositories
    response.map(_.asInstanceOf[Map[Long, Repository]])
      .map(map => map.values.toList)
  }
}
