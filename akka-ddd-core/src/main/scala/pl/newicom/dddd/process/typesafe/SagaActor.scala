package pl.newicom.dddd.process.typesafe

import akka.actor.{ActorPath, ActorLogging}
import akka.contrib.pattern.ReceivePipeline
import akka.persistence.{RecoveryCompleted, AtLeastOnceDelivery, PersistentActor}
import org.joda.time.DateTime
import pl.newicom.dddd.actor.{PassivationConfig, GracefulPassivation}
import pl.newicom.dddd.aggregate._
import pl.newicom.dddd.delivery.protocol.alod.{Processed, Delivered}
import pl.newicom.dddd.messaging.MetaData._
import pl.newicom.dddd.messaging.command.CommandMessage
import pl.newicom.dddd.messaging.{Message, Deduplication}
import pl.newicom.dddd.messaging.event.EventMessage
import pl.newicom.dddd.scheduling.ScheduleEvent
import shapeless.ops.coproduct.{Selector, Inject}
import shapeless.{:+:, CNil, Coproduct}

object SagaActor {
  trait Create[Saga] {
    def apply[State, In <: Coproduct, C <: Coproduct](a0: SagaBehavior[Saga, State, In, C])(implicit handler: Apply[a0.type, State, In], injectIn: InjectAny[In], init: ApplyFirst[a0.type, State, C], injectC: InjectAny[C]): SagaActor[a0.type, State, In, C]
  }

  def create[Saga] = new Create[Saga] {
    def apply[State, In <: Coproduct, C <: Coproduct](a0: SagaBehavior[Saga, State, In, C])(implicit handler: Apply[a0.type, State, In], injectIn: InjectAny[In], init: ApplyFirst[a0.type, State, C], injectC: InjectAny[C]): SagaActor[a0.type, State, In, C] = new SagaActor
  }
}

class SagaActor[B, State, In <: Coproduct, C <: Coproduct](implicit handler: Apply[B, State, In], init: ApplyFirst[B, State, C], injectIn: InjectAny[In], injectC: InjectAny[C]) extends BusinessEntity with GracefulPassivation with PersistentActor
with ReceivePipeline with Deduplication with AtLeastOnceDelivery with ActorLogging {

  var stateOpt: Option[State] = None

  val pc = PassivationConfig()

  def sagaId = self.path.name

  override def id = sagaId

  override def persistenceId: String = sagaId

  def schedulingOffice: Option[ActorPath] = None

  def sagaOffice: ActorPath = context.parent.path.parent

  private var _lastEventMessage: Option[EventMessage[DomainEvent]] = None

  def eventMessage = _lastEventMessage.get


  override def handleDuplicated(m: Message): Unit = acknowledgeEvent(m)

  override def receiveCommand: Receive = receiveDeliveryReceipt orElse receiveEvent orElse receiveUnexpected

  def deliverMsg(office: ActorPath, msg: Message): Unit = {
    deliver(office)(deliveryId => {
      msg.withMetaAttribute(DeliveryId, deliveryId)
    })
  }

  def deliverCommand(office: ActorPath, command: Command): Unit = {
    deliverMsg(office, CommandMessage(command).causedBy(eventMessage))
  }

  def receiveDeliveryReceipt: Receive = {
    case receipt: Delivered =>
      persist(receipt)(updateState)
  }

  def schedule(event: DomainEvent, deadline: DateTime, correlationId: EntityId = sagaId): Unit = {
    schedulingOffice.fold(throw new UnsupportedOperationException("Scheduling Office is not defined.")) { schOffice =>
      val command = ScheduleEvent("global", sagaOffice, deadline, event)
      deliverMsg(schOffice, CommandMessage(command).withCorrelationId(correlationId))
    }
  }

  def receiveEvent: Receive = ???

  def raise(em: EventMessage[DomainEvent]): Unit =
    persist(em) { persisted =>
      log.debug("Event message persisted: {}", persisted)
      updateState(persisted)
      acknowledgeEvent(persisted)
    }

  override def receiveRecover: Receive = {
    case rc: RecoveryCompleted =>
    case msg: Any => updateState(msg)
  }

  private def updateState(msg: Any): Unit = msg match {
    case em: EventMessage[_] =>
      _lastEventMessage = Some(em)
      messageProcessed(em)
      applyEvent(em.event)
    case receipt: Delivered =>
      confirmDelivery(receipt.deliveryId)
      log.debug(s"Delivery of message confirmed (receipt: $receipt)")
      PartialFunction.condOpt(receipt) { case p: Processed => p.result }.foreach(applyEvent)
  }

  def applyEvent: Any => Unit = { in =>
    stateOpt.map { state =>
      injectIn(in).map(handler(state))
    }.getOrElse(injectC(in).map(init.apply)) match {
      case Some(reaction) => runReaction(reaction)
      case None => ()
    }
  }

  private def acknowledgeEvent(em: Message) {
    val deliveryReceipt = em.deliveryReceipt()
    sender() ! deliveryReceipt
    log.debug(s"Delivery receipt (for received event) sent ($deliveryReceipt)")
  }

  def receiveUnexpected: Receive = {
    case em: EventMessage[DomainEvent] => handleUnexpectedEvent(em)
  }

  def handleUnexpectedEvent(em: EventMessage[DomainEvent]): Unit = {
    log.warning(s"Unhandled: $em") // unhandled event should be redelivered by SagaManager
  }

  def runReaction(reaction: Reaction[State]): Unit = reaction match {
    case ChangeState(s) =>
      stateOpt = Some(s)

    case SendCommand(officePath, command) =>
      deliverCommand(officePath.value, command)

    case And(f, s) =>
      runReaction(f)
      runReaction(s)

    case Ignore => ()
  }
}
