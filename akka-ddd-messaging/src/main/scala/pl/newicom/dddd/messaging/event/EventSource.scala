package pl.newicom.dddd.messaging.event

import akka.stream.scaladsl.Source
import pl.newicom.dddd.aggregate.DomainEvent

object EventSource {

  case class EventReceived[+E <: DomainEvent](em: EventMessage[E], position: Long)

  trait DemandCallback {
    def onEventProcessed(): Unit
  }
}

trait EventSource[+Out, +Mat] {
  def apply(stream: EventStream, fromPositionExclusive: Option[Long]): Source[Out, Mat]
}
