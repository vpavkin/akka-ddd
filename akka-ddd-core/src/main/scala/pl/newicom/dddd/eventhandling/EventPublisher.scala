package pl.newicom.dddd.eventhandling

import akka.actor.ActorRef
import pl.newicom.dddd.aggregate.DomainEvent
import pl.newicom.dddd.messaging.event.DomainEventMessage

trait EventPublisher[E <: DomainEvent] extends EventHandler[E] {

  override abstract def handle(senderRef: ActorRef, event: DomainEventMessage[E]): Unit = {
    publish(event)
    super.handle(senderRef, event)
  }

  def publish(event: DomainEventMessage[E])
}
