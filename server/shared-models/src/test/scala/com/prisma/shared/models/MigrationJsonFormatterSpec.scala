package com.prisma.shared.models

import com.prisma.utils.json.JsonUtils
import org.scalatest.{FlatSpec, Matchers, WordSpecLike}
import play.api.libs.json.Json

class MigrationJsonFormatterSpec extends WordSpecLike with Matchers with JsonUtils {
  import ProjectJsonFormatter.migrationFormat

  "it should read the current format" in {
    val json =
      """{
        |    "rolledBack" : 1,
        |    "applied" : 2,
        |    "projectId" : "the-projectId",
        |    "errors" : [],
        |    "revision" : 3.0,
        |    "schema" : {
        |        "models" : [ 
        |            {
        |                "name" : "TestModel",
        |                "stableIdentifier" : "whatever",
        |                "isEmbedded" : false,
        |                "fields" : [ 
        |                    {
        |                        "typeIdentifier" : "GraphQLID",
        |                        "name" : "id",
        |                        "isReadonly" : false,
        |                        "isList" : false,
        |                        "isUnique" : true,
        |                        "isRequired" : true,
        |                        "isHidden" : false,
        |                        "isAutoGenerated" : false
        |                    }
        |                ]
        |            }
        |        ],
        |        "relations" : [],
        |        "enums" : []
        |    },
        |    "status" : "SUCCESS",
        |    "startedAt" : null,
        |    "steps" : [ 
        |        {
        |            "name" : "TestModel",
        |            "discriminator" : "CreateModel"
        |        }
        |    ],
        |    "finishedAt" : null,
        |    "functions" : []
        |}
      """.stripMargin.parseJson

    val migration = json.as[Migration]

    migration.rolledBack should be(1)
    migration.applied should be(2)
    migration.projectId should be("the-projectId")
    migration.errors should have(size(0))
    migration.schema.models should have(size(1))
    val model = migration.schema.models.head
    model.name should be("TestModel")
    model.stableIdentifier should be("whatever")
    model.isEmbedded should be(false)
    model.fields should have(size(1))
    val field = model.fields.head
    field.typeIdentifier should be(TypeIdentifier.Cuid)
    field.name should be("id")
    migration.revision should be(3)
    migration.status should be(MigrationStatus.Success)
    migration.steps should equal(Vector(CreateModel("TestModel")))
    migration.functions should be(empty)
  }

  "it should write the current format" in {
    val migration = Migration(
      projectId = "projectId",
      revision = 1,
      schema = Schema.empty,
      functions = Vector.empty,
      status = MigrationStatus.Success,
      applied = 2,
      rolledBack = 3,
      steps = Vector.empty,
      errors = Vector.empty,
      startedAt = None,
      finishedAt = None,
      previousSchema = Schema.empty
    )

    val json = Json.toJson(migration)
    println(json)
    json should be(
      """{
           "projectId":"projectId",
           "revision":1,
           "schema":{"models":[],"relations":[],"enums":[]},"functions":[],"status":"SUCCESS",
           "applied":2,
           "rolledBack":3,
           "steps":[],
           "errors":[],
           "startedAt":null,
           "finishedAt":null
         }
      """.stripMargin.parseJson()
    )
  }
}
