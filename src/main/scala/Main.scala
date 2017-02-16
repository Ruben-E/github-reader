import java.time.LocalDateTime

import akka.actor.{ActorSystem, Props}
import nl.rubenernst.mrdeveloper.scala.service.{GithubReaderActor, Read, RepositoryProcessor}

/**
  * Created by rubenernst on 19/12/2016.
  */
object Main extends App {
  val system = ActorSystem("GithubReader")
  val log = akka.event.Logging.getLogger(system, this)
  log.info(s"Started!")

  val stateActor = system.actorOf(Props[RepositoryProcessor])
  val readerActor = system.actorOf(Props(new GithubReaderActor(stateActor)))
  readerActor ! Read(LocalDateTime.now)

}
