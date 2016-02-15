package pl.newicom.dddd.view.sql

import pl.newicom.dddd.aggregate.DomainEvent
import pl.newicom.dddd.messaging.event.{EventMessage, DomainEventMessage}
import pl.newicom.dddd.view.sql.Projection.ProjectionAction
import slick.dbio.Effect.All
import slick.dbio.{DBIOAction, Effect, NoStream}

object Projection {
  type ProjectionAction[E <: Effect] = DBIOAction[Unit, NoStream, E]
}

trait Projection[-E <: DomainEvent] extends DBActionHelpers {
  def consume(event: DomainEventMessage[E]): ProjectionAction[All]
}
