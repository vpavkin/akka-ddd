package pl.newicom.dddd.aggregate

import akka.actor.Status.Failure
import akka.actor._
import akka.contrib.pattern.ReceivePipeline
import akka.persistence._
import pl.newicom.dddd.actor.{BusinessEntityActorFactory, GracefulPassivation, PassivationConfig}
import pl.newicom.dddd.aggregate
import pl.newicom.dddd.aggregate.error.AggregateRootNotInitializedException
import pl.newicom.dddd.eventhandling.{EventPublisher, EventHandler}
import pl.newicom.dddd.messaging.command.CommandMessage
import pl.newicom.dddd.messaging.event.{AggregateSnapshotId, DomainEventMessage, EventMessage}
import pl.newicom.dddd.messaging.{Deduplication, Message}
import pl.newicom.dddd.office.{AggregateContract, OfficeInfo}
import akka.pattern.pipe
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag
import scalaz.\/
import scalaz.Scalaz._
import scala.concurrent.duration.{Duration, _}
import scala.util.{Success, Try}

sealed trait AggregateReaction[+Evt, +Err]
object AggregateReaction {
  case class Accept[+Evt](event: Evt) extends AggregateReaction[Evt, Nothing]
  case class Collaborate[+Evt, +Err](future: ExecutionContext => Future[AggregateReaction[Evt, Err]]) extends AggregateReaction[Evt, Err]
  case class Reject[+Err](error: Err) extends AggregateReaction[Nothing, Err]
  case object Ignore extends AggregateReaction[Nothing, Nothing]
}


trait AggregateRoot[Office] extends AggregateContract[Office] {
  import AggregateReaction._

  type State
  type CommandProcessingResult = AggregateReaction[EventImpl, ErrorImpl]
  type ProcessCommand = CommandImpl => CommandProcessingResult
  type ApplyEvent = EventImpl => State
  type ProcessFirstCommand = PartialFunction[CommandImpl, CommandProcessingResult]
  type ApplyFirstEvent = PartialFunction[EventImpl, State]

  def processCommand(state: State): ProcessCommand
  def applyEvent(state: State): ApplyEvent
  def processFirstCommand: ProcessFirstCommand
  def applyFirstEvent: ApplyFirstEvent

  def accept(e: EventImpl): CommandProcessingResult = Accept(e)
  def reject(e: ErrorImpl): CommandProcessingResult = Reject(e)
  def collaborate(f: ExecutionContext => Future[CommandProcessingResult]): CommandProcessingResult = Collaborate(f)
  def ignore: CommandProcessingResult = Ignore
}

object AggregateRoot {
  type Aux[O, S0, CommandImpl0 <: Command, EventImpl0 <: aggregate.DomainEvent, ErrorImpl0] = AggregateRoot[O] {
  type State =  S0
    type EventImpl = EventImpl0
    type CommandImpl = CommandImpl0
    type ErrorImpl = ErrorImpl0
  }
}

abstract class AggregateRootActorFactory[A] extends BusinessEntityActorFactory[A] {
  def props(pc: PassivationConfig): Props
  def inactivityTimeout: Duration = 1.minute
}

abstract class AggregateRootActor[O, S, Cm <: Command, Ev <: DomainEvent, Er](val pc: PassivationConfig)(implicit behavior: AggregateRoot.Aux[O, S, Cm, Ev, Er], officeInfo: OfficeInfo[O], ev: ClassTag[Ev], cm: ClassTag[Cm])
  extends BusinessEntity with GracefulPassivation with PersistentActor with Stash
  with EventHandler[Ev] with ReceivePipeline with Deduplication with ActorLogging {

  import AggregateReaction._
  implicit def ec: ExecutionContext = context.system.dispatcher

  private case class CollaborationResult(reaction: AggregateReaction[Ev, Er])

  private var stateOpt: Option[S] = None
  private var lastCommandMessage: Option[CommandMessage] = None

  override def persistenceId: String = s"${officeInfo.name}-$id"
  override def id = self.path.name

  override def receiveRecover = {
    case em: EventMessage[Ev] => updateState(em)
  }

  override def receiveCommand: Receive = {
    case commandMessage: CommandMessage =>
      log.debug(s"Received: $commandMessage")
      lastCommandMessage = Some(commandMessage)
      commandMessage.command match {
        case command: Cm => handleCommand(commandMessage)(command)
        case other => unhandled(other)
      }
  }

  def handleCommand(commandMessage: CommandMessage): Cm => Unit = { command =>
    val reactionOpt = stateOpt match {
      case Some(state) => Some(behavior.processCommand(state)(command))
      case None => behavior.processFirstCommand.lift(command)
    }
    reactionOpt match {
      case Some(reaction) => handleReaction(commandMessage)(reaction)
      case None => unhandled(command)
    }
  }

  def handleReaction(commandMessage: CommandMessage): AggregateReaction[Ev, Er] => Unit = {
    case Accept(evt) => raise(commandMessage)(evt)
    case Reject(err) => acknowledgeCommand(commandMessage)(err.left[Ev])
    case Collaborate(f) =>
      context.become(awaitinCollaborationResult(commandMessage), discardOld = false)
      f(ec).map(CollaborationResult).pipeTo(self)
    case Ignore =>
  }

  def awaitinCollaborationResult(commandMessage: CommandMessage): Receive = {
    case CollaborationResult(reaction) =>
      unstashAll()
      context.unbecome()
      handleReaction(commandMessage)(reaction)
    case Failure(reason) => throw reason
    case _ => stash()
  }

  private def raise(commandMessage: CommandMessage)(event: Ev) {
    persist(EventMessage(event).causedBy(commandMessage)) { persisted =>
      log.info("Event persisted: {}", event)
      updateState(persisted)
      handle(sender(), toDomainEventMessage(persisted))
    }
  }

  def updateState(persisted: EventMessage[Ev]) {
    val event = persisted.event
    val nextState = stateOpt.map(state => behavior.applyEvent(state)(event)).getOrElse(behavior.applyFirstEvent(event))
    stateOpt = Option(nextState)
    messageProcessed(persisted)
  }

  def toDomainEventMessage(persisted: EventMessage[Ev]): DomainEventMessage[Ev] =
    DomainEventMessage(persisted, AggregateSnapshotId(id, lastSequenceNr))
      .addMetadata(persisted.metadata)

  override def handle(senderRef: ActorRef, eventMessage: DomainEventMessage[Ev]) {
    lastCommandMessage.foreach(acknowledgeCommandProcessed(Success(eventMessage.event.right[Er])))
  }

  def acknowledgeCommand(commandMessage: CommandMessage)(result: Er \/ Ev) =
    acknowledgeCommandProcessed(Success(result))(commandMessage)

  def acknowledgeCommandProcessed(result: Try[Any] = Success("Ok"))(msg: Message) {
    val deliveryReceipt = msg.deliveryReceipt(result)
    sender() ! deliveryReceipt
    log.debug(s"Delivery receipt (for received command) sent ($deliveryReceipt)")
  }

  def handleDuplicated(msg: Message) = acknowledgeCommandProcessed()(msg)
}
