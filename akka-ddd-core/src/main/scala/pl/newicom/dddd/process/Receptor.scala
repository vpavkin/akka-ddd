package pl.newicom.dddd.process

import akka.actor.ActorPath
import pl.newicom.dddd.aggregate.{DomainEvent, EntityId}
import pl.newicom.dddd.delivery.AtLeastOnceDeliverySupport
import pl.newicom.dddd.messaging.event._
import pl.newicom.dddd.messaging.{Message, MetaData}
import pl.newicom.dddd.office.OfficeInfo
import pl.newicom.dddd.process.ReceptorConfig.{ReceiverResolver, StimuliSource, Transduction}

object ReceptorConfig {
  type Transduction = PartialFunction[EventMessage[DomainEvent], Message]
  type ReceiverResolver = PartialFunction[Message, ActorPath]
  type StimuliSource = EventStream
}

abstract class ReceptorConfig {
  def stimuliSource: StimuliSource
  def transduction: Transduction
  def receiverResolver: ReceiverResolver
}

trait ReceptorGrammar {
  def reactTo[A : OfficeInfo](subChannel: Option[String] = None):     ReceptorGrammar
  def applyTransduction(transduction: Transduction):                  ReceptorGrammar
  def route(receiverResolver: ReceiverResolver):                      ReceptorConfig
  def propagateTo(receiver: ActorPath):                               ReceptorConfig
}

case class ReceptorBuilder(
    stimuliSource: StimuliSource = null,
    transduction: Transduction = {case em => em},
    receiverResolver: ReceiverResolver = null)
  extends ReceptorGrammar { self =>

  def reactTo[A : OfficeInfo]: ReceptorBuilder = {
    reactTo[A](None)
  }

  def reactTo[A : OfficeInfo](clerk: Option[EntityId]) = {
    val officeInfo: OfficeInfo[_] = implicitly[OfficeInfo[_]]
    val officeName = officeInfo.name
    val eventStream = clerk.fold[EventStream](OfficeEventStream(officeInfo)) { c => ClerkEventStream(officeName, c) }
    reactToStream(eventStream)
  }

  def reactToStream(eventStream: EventStream) = {
    copy(stimuliSource = eventStream)
  }

  def applyTransduction(transduction: Transduction) =
    copy(transduction = transduction)

  def route(_receiverResolver: ReceiverResolver) =
    new ReceptorConfig() {
      def stimuliSource = self.stimuliSource
      def transduction = self.transduction
      def receiverResolver = _receiverResolver
    }

  def propagateTo(_receiver: ActorPath) = route({case _ => _receiver})
}

abstract class Receptor extends AtLeastOnceDeliverySupport {
  this: EventStreamSubscriber =>

  def config: ReceptorConfig

  def deadLetters = context.system.deadLetters.path

  def destination(msg: Message) = config.receiverResolver.applyOrElse(msg, (any: Message) => deadLetters)

  override lazy val persistenceId: String = s"Receptor-${config.stimuliSource.officeName}-${self.path.hashCode}"

  override def recoveryCompleted(): Unit =
    subscribe(config.stimuliSource, lastSentDeliveryId)

  override def receiveCommand: Receive =
    receiveEvent(metaDataProvider).orElse(deliveryStateReceive).orElse {
      case other =>
        log.warning(s"RECEIVED: $other")
    }

  override def eventReceived(em: EventMessage[DomainEvent], position: Long): Unit =
    config.transduction.lift(em).foreach { msg =>
      deliver(msg, deliveryId = position)
    }

  def metaDataProvider(em: EventMessage[DomainEvent]): Option[MetaData] = None

}
