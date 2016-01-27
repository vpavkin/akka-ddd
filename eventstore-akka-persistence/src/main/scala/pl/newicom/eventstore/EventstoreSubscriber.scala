package pl.newicom.eventstore

import java.util.concurrent.TimeUnit

import akka.NotUsed
import akka.actor._
import akka.stream.scaladsl._
import akka.stream.{FlowShape, ActorMaterializer, OverflowStrategy}
import eventstore.EventNumber._
import eventstore._
import eventstore.pipeline.TickGenerator.{Tick, Trigger}
import pl.newicom.dddd.aggregate.DomainEvent
import pl.newicom.dddd.messaging.event.EventStreamSubscriber.{InFlightMessagesCallback, EventReceived}
import pl.newicom.dddd.messaging.event.{EventStream, _}

import scala.concurrent.duration.FiniteDuration

class DemandController(triggerActor: ActorRef, bufferSize: Int, initialDemand: Int = 20) extends InFlightMessagesCallback {

  increaseDemand(initialDemand)

  def onChanged(messagesInFlight: Int): Unit = {
    increaseDemand(bufferSize - messagesInFlight)
  }

  private def increaseDemand(increaseValue: Int): Unit =
    for (i <- 1 to increaseValue)
      triggerActor ! Tick(FiniteDuration(0, TimeUnit.MILLISECONDS))
}

trait EventstoreSubscriber extends EventStreamSubscriber with EventstoreSerializationSupport with ActorLogging {
  this: Actor =>

  override def system = context.system

  def bufferSize: Int = 20

  implicit val actorMaterializer = ActorMaterializer()

  def subscribe(stream: EventStream, fromPosExcl: Option[Long]): InFlightMessagesCallback = {

    def eventSource: Source[EventReceived, NotUsed] = {
      val streamId = StreamNameResolver.streamId(stream)
      log.debug(s"Subscribing to $streamId from position $fromPosExcl (exclusive)")
      Source.fromPublisher(
        EsConnection(system).streamPublisher(
          streamId,
          fromPosExcl.map(l => Exact(l.toInt)),
          resolveLinkTos = true
        )
      ).map {
        case EventRecord(_, number, eventData, _) => EventReceived(toEventMessage(eventData), number.value)
        case ResolvedEvent(EventRecord(_, _, eventData, _), linkEvent) => EventReceived(toEventMessage(eventData), linkEvent.number.value)
      }
    }

   def flow: Flow[Trigger, EventReceived, NotUsed] = Flow.fromGraph(GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._
      val zip = b.add(ZipWith(Keep.left[EventReceived, Trigger]))

      eventSource ~> zip.in0
      FlowShape(zip.in1, zip.out)
    })

    val sink = Sink.actorRef(self, onCompleteMessage = Kill)
    val flowSink = flow.toMat(sink)(Keep.both)
    val triggerActor = Source.actorRef(bufferSize, OverflowStrategy.dropNew).to(flowSink).run
    new DemandController(triggerActor, bufferSize)
  }

}
