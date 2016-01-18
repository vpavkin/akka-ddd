package pl.newicom.dddd.process

import akka.actor.ActorPath
import akka.contrib.pattern.ReceivePipeline
import akka.persistence.PersistentActor
import pl.newicom.dddd.aggregate.{DomainEvent, EntityId}
import pl.newicom.dddd.delivery.{AtLeastOnceDeliverySupport, DeliveryState}
import pl.newicom.dddd.messaging.event.EventStreamSubscriber.{EventReceived, InFlightMessagesCallback}
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

abstract class Receptor extends  AtLeastOnceDeliverySupport with ReceptorPersistencePolicy {
  this: EventStreamSubscriber =>

  override lazy val persistenceId: String = s"Receptor-${config.eventStream.officeName}-${self.path.hashCode}"

  def config: ReceptorConfig

  override val snapshottingConfig = RegularSnapshottingConfig(receiveEvent, 1000)

  def deadLetters = context.system.deadLetters

  var inFlightCallback: Option[InFlightMessagesCallback] = None

  override def recoveryCompleted(): Unit =
    inFlightCallback = Some(subscribe(config.eventStream, lastSentDeliveryId))

  override def receiveCommand: Receive =
    receiveEvent.orElse(deliveryStateReceive).orElse {
      case other => log.warning(s"RECEIVED: $other")
    }

  def metaDataProvider(em: EventMessage[DomainEvent]): Option[MetaData] = None

  def receiveEvent: Receive = {
    case EventReceived(em, position) =>
      config.transduction.lift(em) match {
        case Some((target, msg)) => deliver(target, msg, deliveryId = position)
        case None => deadLetters ! em
      }
  }

  override def deliveryStateUpdated(deliveryState: DeliveryState): Unit =
    inFlightCallback.foreach(_.onChanged(deliveryState.unconfirmedNumber))
}
