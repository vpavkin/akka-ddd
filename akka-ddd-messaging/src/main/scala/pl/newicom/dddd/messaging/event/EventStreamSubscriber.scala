package pl.newicom.dddd.messaging.event

import pl.newicom.dddd.aggregate.DomainEvent
import akka.actor.Actor
import pl.newicom.dddd.messaging.MetaData
import pl.newicom.dddd.messaging.event.EventStreamSubscriber._

object EventStreamSubscriber {

  case class EventReceived(em: EventMessage[DomainEvent], position: Long)

  trait InFlightMessagesCallback {
    def onChanged(messagesInFlight: Int)
  }
}

trait EventStreamSubscriber {
  this: Actor =>

  /**
   * Subscribes this actor (the subscriber) to given event stream.
   * The subscriber will receive events as [[EventReceived]] messages.
   *
   * @param fromPositionExclusive if provided Subscriber will be receiving events
   *                              from given position (exclusively)
   *
   * @return callback that the subscriber should invoke whenever number of messages in flight is changed.
   *         This information could be used by event publisher to control the number of emitted events.
   */
  def subscribe(stream: EventStream, fromPositionExclusive: Option[Long]): InFlightMessagesCallback

  def metaDataProvider(em: EventMessage[DomainEvent]): Option[MetaData]
}
