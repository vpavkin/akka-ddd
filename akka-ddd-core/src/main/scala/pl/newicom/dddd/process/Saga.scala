package pl.newicom.dddd.process

import akka.actor.{ActorPath, ActorLogging, Props}
import akka.contrib.pattern.ReceivePipeline
import akka.persistence.{AtLeastOnceDelivery, PersistentActor, RecoveryCompleted}
import pl.newicom.dddd.actor.{BusinessEntityActorFactory, GracefulPassivation, PassivationConfig}
import pl.newicom.dddd.aggregate._
import pl.newicom.dddd.delivery.protocol.alod._
import pl.newicom.dddd.messaging.command.CommandMessage
import pl.newicom.dddd.messaging.event.EventMessage
import pl.newicom.dddd.messaging.{Deduplication, Message}
import pl.newicom.dddd.office.OfficeInfo
import pl.newicom.dddd.process.typesafe._
import shapeless._
import shapeless.ops.coproduct.Folder

import scala.reflect.ClassTag
import scalaz.syntax.id._

sealed trait EventDecision
object EventDecision {
  case object Accept extends EventDecision
  case object Ignore extends EventDecision
}

trait ReceiveEvent[S] extends Poly with CaseComposition with Decisions {
  type Case[A] = poly.Case[this.type,  A :: HNil]

  class CaseBuilder[A] {
    def apply(fn: ((Option[S], A)) => EventDecision) = new Case[A] {
      override type Result = Option[S] => EventDecision
      val value = (l: A ::HNil) => l match {
        case a::HNil => s: Option[S] => fn(s, a)
      }
    }
  }

  def at[A]
  = new CaseBuilder[A]
}

trait ApplyEvent[S] extends Poly with CaseComposition with Reactions[S] {
  type Case[A] = poly.Case[this.type,  A :: HNil]

  class CaseBuilder[A] {
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

  def at[A]
  = new CaseBuilder[A]
}

trait ResolveId extends Poly with CaseComposition {
  type Case[A] = poly.Case[this.type,  A :: HNil]

  class CaseBuilder[A] {
    def apply(fn:  A => EntityId) = new Case[A] {
      override type Result = EntityId
      val value = (l: A ::HNil) => l match {
        case a::HNil => fn(a)
      }
    }
  }

  def at[A]
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
  def props(passivationConfig: PassivationConfig, sagaConfig: SagaConfig)(implicit f1: Folder.Aux[sagaConfig.resolveId.type, sagaConfig.Input, EntityId],
                                                                          f2: Folder.Aux[sagaConfig.receiveEvent.type, sagaConfig.Input, Option[sagaConfig.State] => EventDecision],
                                                                          f3: Folder.Aux[sagaConfig.applyEvent.type, sagaConfig.Input, Option[sagaConfig.State] => EventReaction[sagaConfig.State]],
                                                                          in: InjectAny[sagaConfig.Input], sct: ClassTag[sagaConfig.State]) = {
    import sagaConfig._
    Props(new Saga[Input, State](passivationConfig, sagaConfig.name, _.fold(receiveEvent), _.fold(applyEvent)))
  }
}

class Saga[In <: Coproduct, State](val pc: PassivationConfig, name: String, receiveEvent: In => Option[State] => EventDecision, react: In => Option[State] => EventReaction[State])(implicit In: InjectAny[In], sct: ClassTag[State])
  extends GracefulPassivation with PersistentActor with ReceivePipeline
    with Deduplication with AtLeastOnceDelivery with ActorLogging {

  override def persistenceId: String = name + "-" + id

  def id = self.path.name

  var state: Option[State] = None

  private var _lastEventMessage: Option[EventMessage[DomainEvent]] = None

  /**
   * Event message being processed. Not available during recovery
   */
  def lastEventMessage = _lastEventMessage.get

  private [dddd] def deliverMsg(office: ActorPath, msg: Message): Unit = deliver(office)(id => msg.withDeliveryId(id))
  private [dddd] def deliverCommand(office: ActorPath, command: Command): Unit = deliverMsg(office, CommandMessage(command).causedBy(lastEventMessage))

  def handleDuplicated(m: Message) = acknowledgeMessage(m)

  override def receiveCommand: Receive = receiveDeliveryReceipt orElse receiveEventMessage orElse receiveUnexpected

  def receiveDeliveryReceipt: Receive = {
    case receipt: Delivered => persist(receipt)(updateStateWithDeliveryReceipt)
  }

  def receiveEventMessage: Receive = {
    case em @ EventMessage(_, In(msg)) =>
      receiveEvent(msg)(state) match {
        case EventDecision.Accept => raise(em)
        case EventDecision.Ignore => ()
      }
  }

  override def receiveRecover: Receive = {
    case RecoveryCompleted => ()
    case em: EventMessage[DomainEvent] => updateStateWithEvent(em)
    case receipt: Delivered => updateStateWithDeliveryReceipt(receipt)
  }

  private def raise(em: EventMessage[DomainEvent]): Unit =
    persist(em) { persisted =>
      log.debug("Event message persisted: {}", persisted)
      updateStateWithEvent(persisted)
      acknowledgeMessage(persisted)
    }


  def applyReceipt: PartialFunction[Delivered, Unit] = PartialFunction.empty

  private def updateStateWithEvent(em: EventMessage[DomainEvent]) = {
    messageProcessed(em)
    applyEvent(em)
  }

  def applyEvent(em: EventMessage[DomainEvent]) = {
    em.event match {
      case In(in) => react(in)(state) <| runReaction
      case _ => ()
    }
  }

  def runReaction: EventReaction[State] => Unit = {
    case ChangeState(newState: State) => state = Some(newState)
    case Deliver(officePath, command) => deliverCommand(officePath.value, command)
    case Ignore => ()
    case And(first, second) => Seq(first, second).foreach(runReaction)
  }

  private def updateStateWithDeliveryReceipt(receipt: Delivered) = {
    confirmDelivery(receipt.deliveryId)
    log.debug(s"Delivery of message confirmed (receipt: $receipt)")
    applyReceipt.applyOrElse(receipt, (e: Delivered) => ())
  }

  private def acknowledgeMessage(message: Message) {
    val deliveryReceipt = message.deliveryReceipt()
    sender() ! deliveryReceipt
    log.debug(s"Message [{}] delivery receipt [{}] sent", message, deliveryReceipt)
  }

  def receiveUnexpected: Receive = {
    case em: EventMessage[DomainEvent] => handleUnexpectedEvent(em)
  }

  def handleUnexpectedEvent(em: EventMessage[DomainEvent]): Unit = {
    log.warning("Unhandled event message: [{}]", em)
  }

  override def messageProcessed(m: Message): Unit = {
    _lastEventMessage = Some(m).collect {
      case em @ EventMessage(_, _) => em
    }
    super.messageProcessed(m)
  }

  override def shouldPassivate: Boolean = numberOfUnconfirmed == 0
}