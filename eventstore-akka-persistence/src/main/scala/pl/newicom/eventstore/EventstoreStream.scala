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
import pl.newicom.dddd.messaging.event.DomainEventMessageStream.{DemandCallback, StreamEntry}
import pl.newicom.dddd.messaging.event.{EventStream, _}

import scala.concurrent.duration.FiniteDuration

object EventstoreStream {
  def apply[E <: DomainEvent]()(implicit actorSystem: ActorSystem): DomainEventMessageStream[E, NotUsed] =
    new EventstoreStream
}

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

class EventstoreStream[+E <: DomainEvent](implicit val system: ActorSystem) extends DomainEventMessageStream[E, NotUsed] with EventstoreSerializationSupport {
  def apply(stream: EventStream, fromPosExcl: Option[Long]): Source[StreamEntry[E], NotUsed] = {
    val streamId = StreamNameResolver.streamId(stream)
    Source.fromPublisher(
      EsConnection(system).streamPublisher(
        streamId,
        fromPosExcl.map(l => Exact(l.toInt)),
        resolveLinkTos = true
      )
    ).map {
      case EventRecord(_, number, data, _) => StreamEntry(toDomainEventMessage(data), number.value)
      case ResolvedEvent(linkedEvent, linkEvent) => StreamEntry(toDomainEventMessage(linkedEvent.data), linkEvent.number.value)
    }
  }

  def withBackPressure(bufferSize: Int): DomainEventMessageStream[E, DemandCallback] = BackPressuredEventstoreStream(this, bufferSize)
}

object BackPressuredEventstoreStream {
  def apply[E <: DomainEvent](eventSource: DomainEventMessageStream[E, NotUsed], bufferSize: Int = 20)(implicit actorSystem: ActorSystem): DomainEventMessageStream[E, DemandCallback] =
    new BackPressuredEventstoreStream(eventSource, bufferSize)
}

class BackPressuredEventstoreStream[+E <: DomainEvent](eventSource: DomainEventMessageStream[E, NotUsed], bufferSize: Int)(implicit val system: ActorSystem) extends DomainEventMessageStream[E, DemandCallback] {
  def apply(stream: EventStream, fromPosExcl: Option[Long]): Source[StreamEntry[E], DemandCallback] = {
    val events = eventSource(stream, fromPosExcl)
    val triggers = Source.queue[Trigger](bufferSize, OverflowStrategy.backpressure)
      .mapMaterializedValue(trigger => new DemandController(trigger, bufferSize))
    events.zipWithMat(triggers)(Keep.left)(Keep.right)
  }
}
