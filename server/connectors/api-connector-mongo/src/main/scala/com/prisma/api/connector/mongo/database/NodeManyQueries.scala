package com.prisma.api.connector.mongo.database

import com.prisma.api.connector._
import com.prisma.api.connector.mongo.extensions.DocumentToRoot
import com.prisma.api.helpers.LimitClauseHelper
import com.prisma.gc_values.{CuidGCValue, IdGCValue}
import com.prisma.shared.models.{Model, RelationField}
import org.mongodb.scala.model.Filters
import org.mongodb.scala.{Document, FindObservable, MongoCollection, MongoDatabase}

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.existentials

trait NodeManyQueries extends FilterConditionBuilder {
  // Fixme this does not use selected fields
  def getNodes(model: Model, queryArguments: Option[QueryArguments], selectedFields: SelectedFields) = SimpleMongoAction { database =>
    val nodes = helper(model, queryArguments, None, database).map { results: Seq[Document] =>
      results.map { result =>
        val root = DocumentToRoot(model, result)
        PrismaNode(root.idField, root, Some(model.name))
      }
    }

    nodes.map(n => ResolverResult[PrismaNode](queryArguments, n.toVector))
  }

  def helper(model: Model, queryArguments: Option[QueryArguments], extraFilter: Option[Filter] = None, database: MongoDatabase) = {

    val collection: MongoCollection[Document] = database.getCollection(model.dbName)
    val queryArgFilter = queryArguments match {
      case Some(arg) => arg.filter
      case None      => None
    }

    val skipAndLimit = LimitClauseHelper.skipAndLimitValues(queryArguments)

    val mongoFilter = extraFilter match {
      case Some(inFilter) => buildConditionForFilter(Some(AndFilter(Vector(inFilter) ++ queryArgFilter)))
      case None           => buildConditionForFilter(queryArgFilter)
    }

    val cursorCondition = CursorConditionBuilder.buildCursorCondition(queryArguments)

    val baseQuery: FindObservable[Document]      = collection.find(Filters.and(mongoFilter, cursorCondition))
    val queryWithOrder: FindObservable[Document] = OrderByClauseBuilder.queryWithOrder(baseQuery, queryArguments)
    val queryWithSkip: FindObservable[Document]  = queryWithOrder.skip(skipAndLimit.skip)

    val queryWithLimit = skipAndLimit.limit match {
      case Some(limit) => queryWithSkip.limit(limit)
      case None        => queryWithSkip
    }

    queryWithLimit.collect().toFuture
  }

  //these are only used for relations between non-embedded types
  def getRelatedNodes(fromField: RelationField, fromNodeIds: Vector[IdGCValue], queryArguments: Option[QueryArguments], selectedFields: SelectedFields) =
    SimpleMongoAction { database =>
      val manifestation = fromField.relation.inlineManifestation.get
      val model         = fromField.relatedModel_!

      val inFilter: Filter = ScalarListFilter(model.idField_!.copy(name = manifestation.referencingColumn, isList = true), ListContainsSome(fromNodeIds))
      helper(model, queryArguments, Some(inFilter), database).map { results: Seq[Document] =>
        val groups: Map[CuidGCValue, Seq[Document]] = fromField.relatedField.isList match {
          case true =>
            val tuples = for {
              result <- results
              id     <- result(manifestation.referencingColumn).asArray().getValues.asScala.map(_.asString()).map(x => CuidGCValue(x.getValue))
            } yield (id, result)
            tuples.groupBy(_._1).mapValues(_.map(_._2))

          case false => results.groupBy(x => CuidGCValue(x(manifestation.referencingColumn).asString().getValue))
        }

        fromNodeIds.map { id =>
          groups.get(id.asInstanceOf[CuidGCValue]) match {
            case Some(group) =>
              val roots                                     = group.map(DocumentToRoot(model, _))
              val prismaNodes: Vector[PrismaNodeWithParent] = roots.map(r => PrismaNodeWithParent(id, PrismaNode(r.idField, r, Some(model.name)))).toVector
              ResolverResult(queryArguments, prismaNodes, parentModelId = Some(id))

            case None =>
              ResolverResult(Vector.empty[PrismaNodeWithParent], hasPreviousPage = false, hasNextPage = false, parentModelId = Some(id))
          }
        }
      }
    }

  //Fixme this does not use all queryarguments
  def countFromModel(model: Model, queryArguments: Option[QueryArguments]) = SimpleMongoAction { database =>
    val collection: MongoCollection[Document] = database.getCollection(model.dbName)

    //    val queryArgFilter = queryArguments match {
//      case Some(arg) => arg.filter
//      case None      => None
//    }
//
//    val skipAndLimit = LimitClauseHelper.skipAndLimitValues(queryArguments)
//
//    val cursorCondition = CursorConditionBuilder.buildCursorCondition(queryArguments)
//    We could try passing the other args into countoptions, but not sure about order
//    val baseQuery2                               = collection.countDocuments(Filters.and(buildConditionForFilter(queryArgFilter), cursorCondition)).toFuture()
//
//    val baseQuery: FindObservable[Document]      = collection(Filters.and(buildConditionForFilter(queryArgFilter), cursorCondition))
//    val queryWithOrder: FindObservable[Document] = OrderByClauseBuilder.queryWithOrder(baseQuery, queryArguments)
//    val queryWithSkip: FindObservable[Document]  = queryWithOrder.skip(skipAndLimit.skip)
//
//    val queryWithLimit = skipAndLimit.limit match {
//      case Some(limit) => queryWithSkip.limit(limit)
//      case None        => queryWithSkip
//    }
//
//    queryWithLimit.collect().toFuture

    collection.countDocuments(buildConditionForFilter(queryArguments.flatMap(_.filter))).toFuture.map(_.toInt)

  }

}
