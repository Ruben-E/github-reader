package nl.rubenernst.mrdeveloper.scala.api

import nl.rubenernst.mrdeveloper.scala.service.Repository
import sangria.schema._


object ApiSchema {

  val Repository = ObjectType(
    "Repository",
    fields[ApiContext, Repository](
      Field("id", LongType, resolve = _.value.id)
    ))

  val ID = Argument("id", StringType)

  val Query = ObjectType(
    "Query", fields[ApiContext, Unit](
      Field("repository", ListType(Repository), resolve = _.ctx.getRepositories)
    ))

  val ApiSchema = Schema(Query)
}