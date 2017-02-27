package nl.rubenernst.mrdeveloper.scala.service

import java.time.LocalDate

import akka.actor.ActorLogging
import akka.event.LoggingReceive
import akka.persistence.PersistentActor
import nl.rubenernst.mrdeveloper.scala.protocol.RepositoryProtocol.NewRepository
import nl.rubenernst.mrdeveloper.scala.{DomainValidation, ValidationKey}

import scala.collection.immutable.{HashMap, ListMap}
import scalaz.Scalaz._
import scalaz._

trait RepositoryValidations {

  case object IdRequired extends ValidationKey

  case object FullNameRequired extends ValidationKey

  def checkStars(stars: Long): Validation[String, Long] = {
    if (stars < 0) s"Stars $stars must be above zero".failure else stars.success
  }
}

case class Repository(id: Long, fullName: String, description: Option[String], stars: Map[LocalDate, Long], totalStars: Long) extends RepositoryValidations {
  def incrementStars(starsToIncrement: Long, date: LocalDate): DomainValidation[Repository] =
    checkStars(starsToIncrement).fold(
      fail => fail.failureNel,
      success => {
        copy(stars = stars + (date -> (stars.getOrElse(date, 0L) + starsToIncrement)), totalStars = totalStars + starsToIncrement).success
      }
    )
}

object Repository extends RepositoryValidations {

  import nl.rubenernst.mrdeveloper.scala.CommonValidations._

  def create(cmd: NewRepository): DomainValidation[Repository] =
    (checkId(cmd.id, IdRequired).toValidationNel |@|
      checkString(cmd.fullName, FullNameRequired).toValidationNel) {
      case (id, fullName) => Repository(id, fullName, cmd.description, HashMap.empty, 0L)
    }
}

final case class RepositoryState(repositories: Map[Long, Repository] = Map.empty) {
  def update(repository: Repository): RepositoryState = copy(repositories = repositories + (repository.id -> repository))

  def get(id: Long): Option[Repository] = repositories.get(id)
}

class RepositoryProcessor extends PersistentActor with ActorLogging {

  import context.dispatcher
  import nl.rubenernst.mrdeveloper.scala.protocol.RepositoryProtocol._

  import scala.concurrent.duration._

  var state = RepositoryState()

  context.system.scheduler.schedule(5 seconds, 5 seconds, self, "print")

  def receiveRecover: Receive = LoggingReceive {
    case cmd: NewRepository => createRepository(cmd).fold(
      fail => sender ! ErrorMessage(s"Error $fail occurred on $cmd"),
      repository => persist(cmd) { _ => updateState(repository) }
    )
    case cmd: StarRepository => starRepository(cmd).fold(
      fail => sender ! ErrorMessage(s"Error $fail occurred on $cmd"),
      repository => persist(cmd) { _ => updateState(repository) }
    )
  }

  def receiveCommand: Receive = LoggingReceive {
    case cmd: NewRepository => createRepository(cmd).fold(
      fail => sender ! ErrorMessage(s"Error $fail occurred on $cmd"),
      repository => persist(cmd) { _ =>
        val event = RepositoryCreated(repository.id, repository.fullName, repository.description)
        updateState(repository)
        context.system.eventStream.publish(event)
      }
    )
    case cmd: StarRepository => starRepository(cmd).fold(
      fail => sender ! ErrorMessage(s"Error $fail occurred on $cmd"),
      repository => persist(cmd) { _ =>
        val event = RepositoryStarred(repository.id, repository.fullName, repository.totalStars)
        updateState(repository)
        context.system.eventStream.publish(event)
      }
    )
    case cmd: GetRepository => sender ! state.get(cmd.id)
    case GetAllRepositories => sender ! state.repositories
    case "print" => {
      println(state.repositories.take(10))
      println(state.repositories.toSeq.sortWith(_._2.totalStars > _._2.totalStars).take(10))
    }
  }

  def persistenceId: String = "repositories"

  def updateState(repository: Repository): Unit = state = state.update(repository)

  def createRepository(cmd: NewRepository): DomainValidation[Repository] =
    state.get(cmd.id) match {
      case Some(repository) => repository.success
      case None => Repository.create(cmd)
    }

  def starRepository(cmd: StarRepository): DomainValidation[Repository] = {
    upsertRepository(NewRepository(cmd.id, cmd.fullName, None)) { repository =>
      repository.incrementStars(cmd.stars, cmd.date)
    }
  }

  def upsertRepository[A <: Repository](cmd: NewRepository)(fn: Repository => DomainValidation[A]): DomainValidation[A] =
    state.get(cmd.id) match {
      case Some(repository) => fn(repository)
      case None =>
        val maybeRepository = createRepository(cmd)
        maybeRepository match {
          case Success(repository) => fn(repository)
          case Failure(fail) => fail.failure
        }
    }
}