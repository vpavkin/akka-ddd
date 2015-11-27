package pl.newicom.dddd.process

import akka.actor.{ReceiveTimeout, ActorLogging, ActorPath, Props}
import akka.contrib.pattern.ReceivePipeline
import akka.persistence.{AtLeastOnceDelivery, PersistentActor, RecoveryCompleted}
import org.joda.time.DateTime
import pl.newicom.dddd.actor.{BusinessEntityActorFactory, GracefulPassivation, PassivationConfig}
import pl.newicom.dddd.aggregate._
import pl.newicom.dddd.delivery.protocol.alod._
import pl.newicom.dddd.messaging.MetaData.DeliveryId
import pl.newicom.dddd.messaging.command.CommandMessage
import pl.newicom.dddd.messaging.event.EventMessage
import pl.newicom.dddd.messaging.{Deduplication, Message}
import pl.newicom.dddd.office.OfficeInfo
import pl.newicom.dddd.process.typesafe.InjectAny
import pl.newicom.dddd.scheduling.ScheduleEvent
import shapeless.ops.coproduct.{Unifier, Mapper}
import shapeless._
import shapeless.syntax.typeable._

import scala.reflect.ClassTag

abstract class SagaActorFactory[A <: Saga[_]] extends BusinessEntityActorFactory[A] {
  import scala.concurrent.duration._

  def props(pc: PassivationConfig): Props
  def inactivityTimeout: Duration = 1.minute
}

/**
 * @param bpsName name of Business Process Stream (bps)
 */
abstract class SagaConfig[A <: Saga[_]](val bpsName: String) extends OfficeInfo[A] {

  def name = bpsName

  /**
   * Correlation ID identifies process instance. It is used to route EventMessage
   * messages created by [[SagaManager]] to [[Saga]] instance,
   */
  def correlationIdResolver: PartialFunction[DomainEvent, EntityId]

  override def isSagaOffice: Boolean = true
}

abstract class Saga[Cm <: Coproduct : ClassTag : InjectAny] extends BusinessEntity with GracefulPassivation with PersistentActor
  with ReceivePipeline with Deduplication with AtLeastOnceDelivery with ActorLogging {

  def sagaId = self.path.name

  override def id = sagaId

  override def persistenceId: String = sagaId

  def schedulingOffice: Option[ActorPath] = None

  def sagaOffice: ActorPath = context.parent.path.parent

  private var _lastEventMessage: Option[EventMessage[DomainEvent]] = None

  /**
   * Event message being processed. Not available during recovery
   */
  def eventMessage = _lastEventMessage.get

  def handleDuplicated(m: Message) = acknowledgeEvent(m)

  override def receiveCommand: Receive = receiveDeliveryReceipt orElse receiveEventMessage orElse receiveUnexpected

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
      persist(receipt)(updateStateWithDeliveryReceipt)
  }

  def schedule(event: DomainEvent, deadline: DateTime, correlationId: EntityId = sagaId): Unit = {
    schedulingOffice.fold(throw new UnsupportedOperationException("Scheduling Office is not defined.")) { schOffice =>
      val command = ScheduleEvent("global", sagaOffice, deadline, event)
      deliverMsg(schOffice, CommandMessage(command).withCorrelationId(correlationId))
    }
  }

  /**
   * Defines business process logic (state transitions).
   * State transition happens when raise(event) is called.
   * No state transition indicates the current event message could have been received out-of-order.
   */


  trait PolyReceive extends Poly { outer =>
    type Case[A] = poly.Case[this.type, A::HNil]

    class CaseBuilder[A] {
      def apply(fn: (A) => Unit): Case[A] { type Result = Unit } = new Case[A] {
        type Result = Unit
        val value = (l: A :: HNil) => l match {
          case a :: HNil => fn(a)
        }
      }
    }

    def at[A] = new CaseBuilder[A]
  }

  val receiveEvent: PolyReceive

  implicit def mapper: Mapper[receiveEvent.type, Cm]

  var currentEm: Option[EventMessage[DomainEvent]] = None

  val unapply = InjectAny[Cm]

  def receiveEventMessage: Receive = {
    case em @ EventMessage(_, unapply(msg)) =>
      currentEm = Some(em)
      msg.map(receiveEvent)
  }

  override def receiveRecover: Receive = { case msg =>
    msg.cast[RecoveryCompleted].map(_ => ())
        .orElse(msg.cast[EventMessage[DomainEvent]].map(updateStateWithEvent))
          .orElse(msg.cast[Delivered].map(updateStateWithDeliveryReceipt)).getOrElse(())
  }

  /**
   * Triggers state transition
   */
  def raise(): Unit = currentEm.foreach { em =>
    persist(em) { persisted =>
      log.debug("Event message persisted: {}", persisted)
      updateStateWithEvent(persisted)
      acknowledgeEvent(persisted)
    }
  }

  /**
   * Event handler called on state transition
   */
  def applyEvent: PartialFunction[DomainEvent, Unit]

  private def updateStateWithEvent(em: EventMessage[DomainEvent]) = {
    messageProcessed(em)
    applyEvent.applyOrElse(em.event, (e: DomainEvent) => ())
  }

  private def updateStateWithDeliveryReceipt(receipt: Delivered) = {
    confirmDelivery(receipt.deliveryId)
    log.debug(s"Delivery of message confirmed (receipt: $receipt)")
    applyEvent.applyOrElse(receipt, (e: DomainEvent) => ())
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

  override def messageProcessed(m: Message): Unit = {
    _lastEventMessage = m match {
      case em @ EventMessage(_, _) =>
        Some(em)
      case _ => None
    }
    super.messageProcessed(m)
  }

  override def shouldPassivate: Boolean = numberOfUnconfirmed == 0
}