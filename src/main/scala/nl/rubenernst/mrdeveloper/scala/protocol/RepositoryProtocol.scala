package nl.rubenernst.mrdeveloper.scala.protocol

/**
  * Created by rubenernst on 21/12/2016.
  */
object RepositoryProtocol {
  sealed trait RepositoryCommand {
    def id: Long
  }

  final case class NewRepository(id: Long, fullName: String, description: Option[String]) extends RepositoryCommand
  final case class StarRepository(id: Long, fullName: String, stars: Int) extends RepositoryCommand

  sealed trait RepositoryEvent {
    def id: Long
  }

  final case class RepositoryCreated(id: Long, fullName: String, description: Option[String]) extends RepositoryEvent
  final case class RepositoryStarred(id: Long, fullName: String, stars: Int) extends RepositoryEvent

  final case class ErrorMessage(data: String)
}
