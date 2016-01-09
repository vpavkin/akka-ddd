package pl.newicom.dddd.eventhandling

import akka.actor.{ActorRef, Actor}
import pl.newicom.dddd.aggregate.DomainEvent
import pl.newicom.dddd.delivery.protocol.Processed
import pl.newicom.dddd.messaging.event.DomainEventMessage

import scala.util.Success

trait LocalPublisher[E <: DomainEvent] extends EventPublisher[E] {
  this: Actor =>

  override abstract def handle(senderRef: ActorRef, event: DomainEventMessage[E]): Unit = {
    publish(event)
    senderRef ! Processed(Success(event.payload))
  }

  override def publish(em: DomainEventMessage[E]) {
    context.system.eventStream.publish(em.event)
  }



}
