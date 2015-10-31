package pl.newicom.dddd.eventhandling

import akka.actor.ActorRef
import pl.newicom.dddd.aggregate.DomainEvent
import pl.newicom.dddd.messaging.event.DomainEventMessage

trait EventHandler[E <: DomainEvent] {
  def handle(senderRef: ActorRef, event: DomainEventMessage[E])
}
