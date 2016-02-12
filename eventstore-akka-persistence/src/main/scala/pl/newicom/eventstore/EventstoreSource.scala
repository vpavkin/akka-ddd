package pl.newicom.eventstore

import java.util.concurrent.TimeUnit

import akka.NotUsed
import akka.actor._
import akka.stream.scaladsl._
import akka.stream.{ActorMaterializer, OverflowStrategy}
import eventstore.EventNumber._
import eventstore._
import eventstore.pipeline.TickGenerator.{Tick, Trigger}
import pl.newicom.dddd.aggregate.DomainEvent
import pl.newicom.dddd.messaging.event.EventSource.{DemandCallback, EventReceived}
import pl.newicom.dddd.messaging.event.{EventStream, _}

import scala.concurrent.duration.FiniteDuration

class DemandController(trigger: SourceQueue[Trigger], initialDemand: Int) extends DemandCallback {

  increaseDemandBy(initialDemand)

  override def onEventProcessed(): Unit = {
    increaseDemand()
  }

  private def increaseDemandBy(increaseValue: Int): Unit =
    for (i <- 1 to increaseValue) increaseDemand()


  @inline private def increaseDemand(): Unit =
    trigger.offer(Tick(FiniteDuration(0, TimeUnit.MILLISECONDS)))
}

class EventStoreSource[+E <: DomainEvent](implicit val system: ActorSystem) extends EventSource[EventReceived[E], NotUsed] with EventstoreSerializationSupport {
  def apply(stream: EventStream, fromPosExcl: Option[Long]): Source[EventReceived[E], NotUsed] = {
    val streamId = StreamNameResolver.streamId(stream)
    Source.fromPublisher(
      EsConnection(system).streamPublisher(
        streamId,
        fromPosExcl.map(l => Exact(l.toInt)),
        resolveLinkTos = true
      )
    ).map {
      case EventRecord(_, number, eventData, _) => EventReceived(toEventMessage[E](eventData), number.value)
      case ResolvedEvent(EventRecord(_, _, eventData, _), linkEvent) => EventReceived(toEventMessage[E](eventData), linkEvent.number.value)
    }
  }

  def withBackPressure(bufferSize: Int): EventSource[EventReceived[E], DemandCallback] = new BackpressuredEventStoreSource(this, bufferSize)
}

object EventStoreSource {
  def apply[E <: DomainEvent]()(implicit actorSystem: ActorSystem): EventSource[EventReceived[E], NotUsed] =
    new EventStoreSource
}

class BackpressuredEventStoreSource[+E <: DomainEvent](eventSource: EventSource[EventReceived[E], NotUsed], bufferSize: Int)(implicit val system: ActorSystem) extends EventSource[EventReceived[E], DemandCallback] with EventstoreSerializationSupport {
  def apply(stream: EventStream, fromPosExcl: Option[Long]): Source[EventReceived[E], DemandCallback] = {
    val events = eventSource(stream, fromPosExcl)
    val triggers = Source.queue[Trigger](bufferSize, OverflowStrategy.backpressure)
      .mapMaterializedValue(trigger => new DemandController(trigger, bufferSize))
    events.zipWithMat(triggers)(Keep.left)(Keep.right)
  }
}

object BackpressuredEventStoreSource {
  def apply[E <: DomainEvent](eventSource: EventSource[EventReceived[E], NotUsed], bufferSize: Int = 20)(implicit actorSystem: ActorSystem): EventSource[EventReceived[E], DemandCallback] =
    new BackpressuredEventStoreSource(eventSource, bufferSize)
}
