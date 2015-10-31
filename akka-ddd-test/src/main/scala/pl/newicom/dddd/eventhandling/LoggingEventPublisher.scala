package pl.newicom.dddd.eventhandling

import akka.actor.Actor
import pl.newicom.dddd.aggregate.DomainEvent
import pl.newicom.dddd.messaging.event.DomainEventMessage

trait LoggingEventPublisher[E <: DomainEvent] extends EventPublisher[E] {
  this: Actor =>

  override def publish(em: DomainEventMessage[E]) {
    println("Published: " + em)
  }

}
