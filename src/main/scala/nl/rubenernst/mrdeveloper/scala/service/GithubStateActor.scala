package nl.rubenernst.mrdeveloper.scala.service

import akka.actor.ActorLogging
import akka.event.LoggingReceive
import akka.persistence.PersistentActor
import nl.rubenernst.mrdeveloper.scala.protocol.RepositoryProtocol.NewRepository
import nl.rubenernst.mrdeveloper.scala.{DomainValidation, ValidationKey}

import scala.collection.immutable.ListMap
import scalaz.Scalaz._
import scalaz._

trait RepositoryValidations {

  case object IdRequired extends ValidationKey

  case object FullNameRequired extends ValidationKey

  def checkStars(stars: Long): Validation[String, Long] = {
    if (stars < 0) s"Stars $stars must be above zero".failure else stars.success
  }
}

case class Repository(id: Long, fullName: String, description: Option[String], stars: Long) extends RepositoryValidations {
  def incrementStars(starsToIncrement: Long): DomainValidation[Repository] =
    checkStars(starsToIncrement).fold(
      fail => fail.failureNel,
      success => copy(stars = stars + starsToIncrement).success
    )
}

object Repository extends RepositoryValidations {

  import nl.rubenernst.mrdeveloper.scala.CommonValidations._

  def create(cmd: NewRepository): DomainValidation[Repository] =
    (checkId(cmd.id, IdRequired).toValidationNel |@|
      checkString(cmd.fullName, FullNameRequired).toValidationNel) {
      case (id, fullName) => Repository(id, fullName, cmd.description, 0L)
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
    case evt: RepositoryCreated => createRepository(NewRepository(evt.id, evt.fullName, evt.description)).fold(
      fail => log.warning(s"Could not restore $evt because of $fail"),
      repository => updateState(repository)
    )
    case evt: RepositoryStarred => starRepository(StarRepository(evt.id, evt.fullName, evt.stars)).fold(
      fail => log.warning(s"Could not restore $evt because of $fail"),
      repository => updateState(repository)
    )
  }

  def receiveCommand: Receive = LoggingReceive {
    case cmd: NewRepository => createRepository(cmd).fold(
      fail => sender ! ErrorMessage(s"Error $fail occurred on $cmd"),
      repository => persist(RepositoryCreated(repository.id, repository.fullName, repository.description)) {
        event =>
          updateState(repository)
          context.system.eventStream.publish(event)
      }
    )
    case cmd: StarRepository => starRepository(cmd).fold(
      fail => sender ! ErrorMessage(s"Error $fail occurred on $cmd"),
      repository => persist(RepositoryStarred(repository.id, repository.fullName, cmd.stars)) {
        event =>
          updateState(repository)
          context.system.eventStream.publish(event)
      }
    )
    case "print" => println(ListMap(state.repositories.toSeq.sortWith(_._2.stars > _._2.stars): _*).take(10))
  }

  def persistenceId: String = "repositories"

  def updateState(repository: Repository): Unit = {
    state = state.update(repository)
  }

  def createRepository(cmd: NewRepository): DomainValidation[Repository] =
    state.get(cmd.id) match {
      case Some(repository) => s"Repository for $cmd already exists".failureNel
      case None => Repository.create(cmd)
    }

  def starRepository(cmd: StarRepository): DomainValidation[Repository] = {
    upsertRepository(NewRepository(cmd.id, cmd.fullName, None)) { repository =>
      repository.incrementStars(1)
    }
  }

  def updateRepository[A <: Repository](cmd: RepositoryCommand)(fn: Repository => DomainValidation[A]): DomainValidation[A] =
    state.get(cmd.id) match {
      case Some(repository) => fn(repository)
      case None => s"Repository for $cmd does not exists".failureNel
    }

  def upsertRepository(cmd: NewRepository)(fn: Repository => DomainValidation[Repository]): DomainValidation[Repository] =
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