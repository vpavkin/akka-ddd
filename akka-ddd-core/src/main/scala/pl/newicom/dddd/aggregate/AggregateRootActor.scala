package pl.newicom.dddd.aggregate

import akka.actor.Status.Failure
import akka.actor._
import akka.contrib.pattern.ReceivePipeline
import akka.pattern.pipe
import akka.persistence._
import pl.newicom.dddd.actor.{GracefulPassivation, PassivationConfig}
import pl.newicom.dddd.messaging.command.CommandMessage
import pl.newicom.dddd.messaging.event.EventMessage
import pl.newicom.dddd.messaging.{Deduplication, Message}
import pl.newicom.dddd.office.AggregateContract.Aux
import pl.newicom.dddd.office.{AggregateContract, OfficeInfo}
import shapeless.{TypeCase, Typeable}

import scala.concurrent.duration.{Duration, _}
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

sealed trait AggregateReaction[+E, +R]
object AggregateReaction {
  case class Accept[+E](event: E) extends AggregateReaction[E, Nothing]
  case class Collaborate[+E, +R](future: ExecutionContext => Future[AggregateReaction[E, R]]) extends AggregateReaction[E, R]
  case class Reject[+R](rejection: R) extends AggregateReaction[Nothing, R]
  case object Ignore extends AggregateReaction[Nothing, Nothing]
}


trait AggregateRootBehavior[S, C <: Command, E <: DomainEvent, R] {
  import AggregateReaction._

  type CommandProcessingResult = AggregateReaction[E, R]
  type ProcessCommand = C => CommandProcessingResult
  type ApplyEvent = E => S
  type ProcessFirstCommand = PartialFunction[C, CommandProcessingResult]
  type ApplyFirstEvent = PartialFunction[E, S]

  def processCommand(state: S): ProcessCommand
  def applyEvent(state: S): ApplyEvent
  def processFirstCommand: ProcessFirstCommand
  def applyFirstEvent: ApplyFirstEvent

  def accept(e: E): CommandProcessingResult = Accept(e)
  def reject(e: R): CommandProcessingResult = Reject(e)
  def collaborate(f: ExecutionContext => Future[CommandProcessingResult]): CommandProcessingResult = Collaborate(f)
  def ignore: CommandProcessingResult = Ignore
}

abstract class AggregateRootActorFactory {
  def create[O, S, C <: Command, E <: DomainEvent, R]
  (pc: PassivationConfig, behavior: AggregateRootBehavior[S, C, E, R])
  (implicit officeInfo: OfficeInfo[O], contract: AggregateContract.Aux[O, C, E, R], ev: ClassTag[E], cm: ClassTag[C]): AggregateRootActor[O, S, C, E, R]
  def inactivityTimeout: Duration = 1.minute

}

object AggregateRootActorFactory {
  def default: AggregateRootActorFactory = new AggregateRootActorFactory {
    override def create[O, S, Cm <: Command, Ev <: DomainEvent, Er](pc: PassivationConfig, behavior: AggregateRootBehavior[S, Cm, Ev, Er])(implicit officeInfo: OfficeInfo[O], contract: Aux[O, Cm, Ev, Er], ev: ClassTag[Ev], cm: ClassTag[Cm]) =
      new AggregateRootActor[O, S, Cm, Ev, Er](pc, behavior)
  }
}

class AggregateRootActor[O, S, C <: Command, E <: DomainEvent, R]
(val pc: PassivationConfig, behavior: AggregateRootBehavior[S, C, E, R])
(implicit officeInfo: OfficeInfo[O], contract: AggregateContract.Aux[O, C, E, R], ev: ClassTag[E], val Cm: ClassTag[C], caseCMCm: Typeable[CommandMessage[C]])
  extends GracefulPassivation with PersistentActor with Stash with ReceivePipeline with Deduplication[Option[R]] with ActorLogging {

  import AggregateReaction._

  implicit def ec: ExecutionContext = context.system.dispatcher

  private case class CollaborationResult(reaction: AggregateReaction[E, R])

  private var stateOpt: Option[S] = None

  override def persistenceId: String = officeInfo.clerkGlobalId(id)

  def id = self.path.name


  val CommandMessageCm: TypeCase[CommandMessage[C]] = TypeCase[CommandMessage[C]]
  val EventMessageEv: TypeCase[EventMessage[E]] = TypeCase[EventMessage[E]]

  override def receiveRecover = {
    case EventMessageEv(em) => updateState(em)
  }

  override def receiveCommand: Receive = {
    case CommandMessageCm(commandMessage) =>
      log.debug(s"Received: $commandMessage")
      handleCommand(commandMessage)
    case other => unhandled(other)
  }

  def handleCommand(cm: CommandMessage[C]): Unit = {
    val command = cm.command
    val reactionOpt = stateOpt.map(state => behavior.processCommand(state)(command))
      .orElse(behavior.processFirstCommand.lift(command))
    reactionOpt match {
      case Some(reaction) => handleReaction(cm)(reaction)
      case None => unhandled(command)
    }
  }

  def handleReaction(commandMessage: CommandMessage[C]): AggregateReaction[E, R] => Unit = {
    case Accept(evt) => accept(commandMessage)(evt)
    case Reject(err) => reject(commandMessage)(err)
    case Collaborate(f) =>
      context.become(awaitingCollaborationResult(commandMessage), discardOld = false)
      f(ec).map(CollaborationResult).pipeTo(self)(sender())
    case Ignore => ()
  }

  def awaitingCollaborationResult(commandMessage: CommandMessage[C]): Receive = {
    case CollaborationResult(reaction) =>
      unstashAll()
      context.unbecome()
      handleReaction(commandMessage)(reaction)
    case Failure(reason) =>
      log.error(reason, "Collaboration failed unexpectedly")
      unstashAll()
      context.unbecome()
      throw reason
    case _ => stash()
  }

  private def accept(command: CommandMessage[C])(event: E) {
    persist(EventMessage(event).causedBy(command)) { persisted =>
      log.info("Event persisted: {}", event)
      updateState(persisted)
      acknowledgeMessage(command)(None)
    }
  }

  private def reject(command: CommandMessage[C])(err: R): Unit = {
    acknowledgeMessage(command)(Some(err))
  }

  def updateState(persisted: EventMessage[E]) {
    val event = persisted.event
    val nextState = stateOpt.map(state => behavior.applyEvent(state)(event)).getOrElse(behavior.applyFirstEvent(event))
    stateOpt = Option(nextState)
    messageProcessed(persisted.metadata.causationId.get, None)
  }


  def acknowledgeMessage(message: Message)(error: Option[R]) = {
    val deliveryReceipt = message.ack(error)
    sender() ! deliveryReceipt
    log.debug(s"Delivery receipt (for received command) sent ($deliveryReceipt)")
  }


  override def handleDuplicated(m: Message, result: Option[R]): Unit = acknowledgeMessage(m)(result)
}
