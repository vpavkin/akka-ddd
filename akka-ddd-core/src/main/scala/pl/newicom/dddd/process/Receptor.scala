package pl.newicom.dddd.process

import akka.actor.{Kill, ActorPath}
import akka.contrib.pattern.ReceivePipeline
import akka.persistence.PersistentActor
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Source, Keep, Sink}
import pl.newicom.dddd.aggregate.{DomainEvent, EntityId}
import pl.newicom.dddd.delivery.{AtLeastOnceDeliverySupport, DeliveryState}
import pl.newicom.dddd.messaging.event.EventSource.{EventReceived, DemandCallback}
import pl.newicom.dddd.messaging.event._
import pl.newicom.dddd.messaging.{Message, MetaData}
import pl.newicom.dddd.office.OfficeInfo
import pl.newicom.dddd.persistence.{RegularSnapshotting, RegularSnapshottingConfig}
import pl.newicom.dddd.process.ReceptorConfig.ExtractReceiver

case class ReceptorConfig(eventStream: EventStream, transduction: ExtractReceiver)

object ReceptorConfig {
  type ExtractReceiver = PartialFunction[EventMessage[DomainEvent], (ActorPath, Message)]
  trait WithoutTransduction {
    def extractReceiver(transduction: ExtractReceiver): ReceptorConfig

    def propagateTo(receiver: ActorPath): ReceptorConfig = extractReceiver {
      case any => (receiver, any)
    }
  }
  def reactTo[A : OfficeInfo]: WithoutTransduction = reactTo[A](None)

  def reactTo[A : OfficeInfo](clerk: Option[EntityId]): WithoutTransduction = {
    val officeInfo = OfficeInfo[A]
    val eventStream = clerk.fold[EventStream](OfficeEventStream(officeInfo)) { c => ClerkEventStream(officeInfo.name, c) }
    reactToStream(eventStream)
  }

  def reactToStream(eventStream: EventStream): WithoutTransduction = new WithoutTransduction {
    override def extractReceiver(transduction: ExtractReceiver): ReceptorConfig =
      ReceptorConfig(eventStream, transduction)
  }
}


trait ReceptorPersistencePolicy extends ReceivePipeline with RegularSnapshotting {
  this: PersistentActor =>
  override def journalPluginId = "akka.persistence.journal.inmem"
}

abstract class Receptor(eventSource: EventSource[EventReceived[DomainEvent], DemandCallback]) extends  AtLeastOnceDeliverySupport with ReceptorPersistencePolicy {

  override lazy val persistenceId: String = s"Receptor-${config.eventStream.officeName}-${self.path.hashCode}"

  def config: ReceptorConfig
  implicit val actorMaterializer = ActorMaterializer()

  override val snapshottingConfig = RegularSnapshottingConfig(receiveEvent, 1000)

  def deadLetters = context.system.deadLetters

  var demandCallback: Option[DemandCallback] = None

  override def recoveryCompleted(): Unit = {
    log.debug(s"Subscribing to ${config.eventStream} from position $lastSentDeliveryId (exclusive)")
    val callback = eventSource(config.eventStream, lastSentDeliveryId)
      .toMat(Sink.actorRef[EventReceived[DomainEvent]](self, onCompleteMessage = Kill))(Keep.left)
      .run
    demandCallback = Some(callback)
  }


  override def receiveCommand: Receive =
    receiveEvent.orElse(deliveryStateReceive).orElse {
      case other => log.warning(s"RECEIVED: $other")
    }

  def receiveEvent: Receive = {
    case EventReceived(em, position) =>
      config.transduction.lift(em) match {
        case Some((target, msg)) => deliver(target, msg, deliveryId = position)
        case None => deadLetters ! em
      }
  }

  override def deliveryConfirmed(deliveryId: Long): Unit =
    demandCallback.foreach(_.onEventProcessed())
}
