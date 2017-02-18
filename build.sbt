name := "github-reader-akka"

version := "1.0"

scalaVersion := "2.11.8"

resolvers += Resolver.jcenterRepo

libraryDependencies += "com.typesafe.akka" % "akka-actor_2.11" % "2.4.14"
libraryDependencies += "com.typesafe.akka" % "akka-http_2.11" % "10.0.0"
libraryDependencies += "com.typesafe.akka" % "akka-http-spray-json_2.11" % "10.0.0"
libraryDependencies += "com.typesafe.akka" % "akka-persistence_2.11" % "2.4.14"
libraryDependencies += "org.scalaz" %% "scalaz-core" % "7.2.8"
libraryDependencies += "com.okumin" %% "akka-persistence-sql-async" % "0.4.0"
libraryDependencies += "com.github.mauricio" %% "postgresql-async" % "0.2.20"
libraryDependencies += "com.github.dnvriend" %% "akka-persistence-inmemory" % "2.4.17.1"