package pl.newicom.dddd.eventhandling

import akka.actor.ActorRef
import pl.newicom.dddd._
import pl.newicom.dddd.messaging.event.DomainEventMessage

trait EventHandler[E <: aggregate.DomainEvent] {
  def handle(senderRef: ActorRef, event: DomainEventMessage[E])
}
