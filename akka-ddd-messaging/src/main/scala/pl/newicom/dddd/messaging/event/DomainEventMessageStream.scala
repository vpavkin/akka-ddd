package pl.newicom.dddd.messaging.event

import akka.stream.scaladsl.Source
import pl.newicom.dddd.aggregate.DomainEvent
import pl.newicom.dddd.messaging.event.DomainEventMessageStream.StreamEntry

object DomainEventMessageStream {

  case class StreamEntry[+E <: DomainEvent](message: DomainEventMessage[E], position: Long)

  trait DemandCallback {
    def onEventProcessed(): Unit
  }
}

trait DomainEventMessageStream[+E <: DomainEvent, +Mat] {
  def apply(stream: EventStream, fromPositionExclusive: Option[Long]): Source[StreamEntry[E], Mat]
}
