package nl.rubenernst.mrdeveloper.scala.protocol

import java.time.LocalDate


object RepositoryProtocol {
  sealed trait RepositoryCommand {
    def id: Long
  }

  final case class NewRepository(id: Long, fullName: String, description: Option[String]) extends RepositoryCommand
  final case class StarRepository(id: Long, fullName: String, stars: Long, date: LocalDate) extends RepositoryCommand
  final case class GetRepository(id: Long) extends RepositoryCommand
  final case class GetAllRepositories()

  sealed trait RepositoryEvent {
    def id: Long
  }

  final case class RepositoryCreated(id: Long, fullName: String, description: Option[String]) extends RepositoryEvent
  final case class RepositoryStarred(id: Long, fullName: String, stars: Long) extends RepositoryEvent

  final case class ErrorMessage(data: String)
}
