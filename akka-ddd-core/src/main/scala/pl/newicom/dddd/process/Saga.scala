package pl.newicom.dddd.process

import akka.actor.{ActorLogging, ActorPath, Props}
import akka.contrib.pattern.ReceivePipeline
import akka.persistence.{AtLeastOnceDelivery, PersistentActor, RecoveryCompleted}
import pl.newicom.dddd.actor.{GracefulPassivation, PassivationConfig}
import pl.newicom.dddd.aggregate._
import pl.newicom.dddd.delivery.protocol.Delivered
import pl.newicom.dddd.messaging.command.CommandMessage
import pl.newicom.dddd.messaging.event.EventMessage
import pl.newicom.dddd.messaging.{Deduplication, IdentifiedMessage, Message}
import pl.newicom.dddd.office.OfficeInfo
import pl.newicom.dddd.process.typesafe._
import shapeless._
import shapeless.ops.coproduct.Folder

import scala.reflect.ClassTag

sealed trait EventDecision
object EventDecision {
  case object Accept extends EventDecision
  case object Ignore extends EventDecision
  case object Reject extends EventDecision
}

trait ReceiveEvent[S] extends Poly with CaseComposition with Decisions {
  type Case[A <: DomainEvent] = poly.Case[this.type,  A :: HNil]

  class CaseBuilder[A <: DomainEvent] {
    def apply(fn: ((Option[S], A)) => EventDecision) = new Case[A] {
      override type Result = Option[S] => EventDecision
      val value = (l: A ::HNil) => l match {
        case a::HNil => s: Option[S] => fn(s, a)
      }
    }
  }

  def at[A <: DomainEvent]
  = new CaseBuilder[A]
}

trait ApplyEvent[S] extends Poly with CaseComposition with Reactions[S] {
  type Case[A <: DomainEvent] = poly.Case[this.type,  A :: HNil]

  class CaseBuilder[A <: DomainEvent] {
    def apply(fn:  A => Option[S] => EventReaction[S]) = new Case[A] {
      override type Result = Option[S] => EventReaction[S]
      val value = (l: A ::HNil) => l match {
        case a::HNil => fn(a)
      }
    }
  }

  def withState(f: S => EventReaction[S]): Option[S] => EventReaction[S] = {
    case Some(s) => f(s)
    case None => ignore
  }

  def at[A <: DomainEvent]
  = new CaseBuilder[A]
}

trait ResolveId extends Poly with CaseComposition {
  type Case[A <: DomainEvent] = poly.Case[this.type,  A :: HNil]

  class CaseBuilder[A <: DomainEvent] {
    def apply(fn:  A => EntityId) = new Case[A] {
      override type Result = EntityId
      val value = (l: A ::HNil) => l match {
        case a::HNil => fn(a)
      }
    }
  }

  def at[A <: DomainEvent]
  = new CaseBuilder[A]
}

trait SagaConfig {
  def name: String
  type State
  type Input <: Coproduct
  val resolveId: ResolveId
  val receiveEvent: ReceiveEvent[State]
  val applyEvent: ApplyEvent[State]
}

object Saga {
  def props(passivationConfig: PassivationConfig, sagaConfig: SagaConfig)(implicit
                                                                          receiveEventFolder: Folder.Aux[sagaConfig.receiveEvent.type, sagaConfig.Input, Option[sagaConfig.State] => EventDecision],
                                                                          applyEventFolder: Folder.Aux[sagaConfig.applyEvent.type, sagaConfig.Input, Option[sagaConfig.State] => EventReaction[sagaConfig.State]],
                                                                          in: InjectAny[sagaConfig.Input], sct: ClassTag[sagaConfig.State]) = {
    import sagaConfig._
    Props(new Saga[Input, State](passivationConfig, name, in => receiveEventFolder(in), in => applyEventFolder(in)))
  }
}

case class SagaOfficeInfo[In <: Coproduct, State](name: String) extends OfficeInfo[Saga[In, State]] {
  override def isSagaOffice: Boolean = true
}

class Saga[In <: Coproduct, State](val pc: PassivationConfig, name: String, receiveEvent: In => Option[State] => EventDecision, react: In => Option[State] => EventReaction[State])(implicit In: InjectAny[In], sct: ClassTag[State])
  extends GracefulPassivation with PersistentActor with ReceivePipeline
    with Deduplication[Unit] with AtLeastOnceDelivery with ActorLogging {

  override def persistenceId: String = name + "-" + id

  def id = self.path.name

  var state: Option[State] = None

  override def receiveCommand: Receive = receiveDeliveryReceipt orElse receiveEventMessage orElse receiveUnexpected

  def receiveDeliveryReceipt: Receive = {
    case receipt: Delivered => persist(receipt)(updateStateWithDeliveryReceipt)
  }

  def receiveEventMessage: Receive = {
    case em @ EventMessage(_, In(msg)) =>
      val decision = receiveEvent(msg)(state)
      log.info("Saga [{}]. State [{}]. Event [{}]. Decision [{}]", persistenceId, state, msg, decision)
      decision match {
        case EventDecision.Accept => raise(em)
        case EventDecision.Ignore => acknowledgeMessage(em)
        case EventDecision.Reject => ()
      }
  }

  override def receiveRecover: Receive = {
    case RecoveryCompleted => ()
    case em: EventMessage[_] => updateStateWithEvent(em)
    case receipt: Delivered => updateStateWithDeliveryReceipt(receipt)
  }

  private def raise(em: EventMessage[DomainEvent]): Unit =
    persist(em) { persisted =>
      log.debug("Event message persisted: {}", persisted)
      updateStateWithEvent(persisted)
      acknowledgeMessage(persisted)
    }


  private def updateStateWithEvent(em: EventMessage[DomainEvent]) = {
    applyEvent(em)
    messageProcessed(em.id, ())
  }

  private def applyEvent(em: EventMessage[DomainEvent]): Unit = em.event match {
    case In(event) =>
      val reaction = react(event)(state)
      log.info("Saga [{}]. State [{}]. Event [{}]. Reaction [{}]", persistenceId, state, event, reaction)
      runReaction(em)(reaction)
    case _ => ()
  }

  private def updateStateWithDeliveryReceipt(receipt: Delivered) = {
    confirmDelivery(receipt.deliveryId)
    log.debug("Delivery of message confirmed. [{}]", receipt)
    applyReceipt.applyOrElse(receipt, (e: Delivered) => ())
  }

  private def applyReceipt: PartialFunction[Delivered, Unit] = {
    case p @ Delivered(_, _, Some(In(in))) =>
      val reaction = react(in)(state)
      runReaction(p)(reaction)
  }

  private def runReaction(causedBy: IdentifiedMessage): EventReaction[State] => Unit = {
    case ChangeState(newState: State) => state = Some(newState)
    case Deliver(officePath, command) => deliverCommand(officePath.value, command, causedBy)
    case Ignore => ()
    case And(first, second) => Seq(first, second).foreach(runReaction(causedBy))
  }

  private def deliverCommand(office: ActorPath, command: Command, causedBy: IdentifiedMessage): Unit =
    deliver(office)(deliveryId => CommandMessage(command).withDeliveryId(deliveryId).causedBy(causedBy))

  private def acknowledgeMessage(message: Message) {
    val deliveryReceipt = message.ack(())
    sender() ! deliveryReceipt
    log.debug(s"Message [{}] delivery receipt [{}] sent", message, deliveryReceipt)
  }

  def receiveUnexpected: Receive = {
    case em: EventMessage[_] => handleUnexpectedEvent(em)
  }

  def handleUnexpectedEvent(em: EventMessage[DomainEvent]): Unit = {
    log.warning("Unhandled event message: [{}]", em)
  }

  override def handleDuplicated(m: Message, result: Unit): Unit = acknowledgeMessage(m)

  override def shouldPassivate: Boolean = numberOfUnconfirmed == 0
}