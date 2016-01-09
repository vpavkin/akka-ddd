package pl.newicom.dddd.aggregate

import akka.actor.Status.Failure
import akka.actor._
import akka.contrib.pattern.ReceivePipeline
import akka.pattern.pipe
import akka.persistence._
import pl.newicom.dddd.actor.{GracefulPassivation, PassivationConfig}
import pl.newicom.dddd.eventhandling.EventHandler
import pl.newicom.dddd.messaging.command.CommandMessage
import pl.newicom.dddd.messaging.event.{AggregateSnapshotId, DomainEventMessage, EventMessage}
import pl.newicom.dddd.messaging.{Deduplication, Message}
import pl.newicom.dddd.office.OfficeContract.Aux
import pl.newicom.dddd.office.{OfficeContract, OfficeInfo}

import scala.concurrent.duration.{Duration, _}
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag
import scala.util.Success
import scalaz.Scalaz._
import scalaz.\/

sealed trait AggregateReaction[+Evt, +Err]
object AggregateReaction {
  case class Accept[+Evt](event: Evt) extends AggregateReaction[Evt, Nothing]
  case class Collaborate[+Evt, +Err](future: ExecutionContext => Future[AggregateReaction[Evt, Err]]) extends AggregateReaction[Evt, Err]
  case class Reject[+Err](error: Err) extends AggregateReaction[Nothing, Err]
  case object Ignore extends AggregateReaction[Nothing, Nothing]
}


trait AggregateRootBehavior[State, CommandImpl <: Command, EventImpl <: DomainEvent, ErrorImpl] {
  import AggregateReaction._

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

abstract class AggregateRootActorFactory {
  def create[O, S, Cm <: Command, Ev <: DomainEvent, Er]
  (pc: PassivationConfig, behavior: AggregateRootBehavior[S, Cm, Ev, Er])
  (implicit officeInfo: OfficeInfo[O], contract: OfficeContract.Aux[O, Cm, Ev, Er],  ev: ClassTag[Ev], cm: ClassTag[Cm]): AggregateRootActor[O, S, Cm, Ev, Er]
  def inactivityTimeout: Duration = 1.minute

}

object AggregateRootActorFactory {
  def default: AggregateRootActorFactory = new AggregateRootActorFactory {
    override def create[O, S, Cm <: Command, Ev <: DomainEvent, Er](pc: PassivationConfig, behavior: AggregateRootBehavior[S, Cm, Ev, Er])(implicit officeInfo: OfficeInfo[O], contract: Aux[O, Cm, Ev, Er], ev: ClassTag[Ev], cm: ClassTag[Cm]) =
      new AggregateRootActor[O, S, Cm, Ev, Er](pc, behavior) {}
  }
}

abstract class AggregateRootActor[O, S, Cm <: Command, Ev <: DomainEvent, Er]
(val pc: PassivationConfig, behavior: AggregateRootBehavior[S, Cm, Ev, Er])
(implicit officeInfo: OfficeInfo[O], contract: OfficeContract.Aux[O, Cm, Ev, Er], ev: ClassTag[Ev], val M: ClassTag[Cm])
  extends GracefulPassivation with PersistentActor with Stash with ReceivePipeline with Deduplication[CommandMessage, Ev] with ActorLogging {

  import AggregateReaction._
  implicit def ec: ExecutionContext = context.system.dispatcher

  private case class CollaborationResult(reaction: AggregateReaction[Ev, Er])

  private var stateOpt: Option[S] = None

  override def persistenceId: String = officeInfo.clerkGlobalId(id)
  def id = self.path.name

  override def receiveRecover = {
    case em: EventMessage[Ev] => updateState(em)
  }

  override def receiveCommand: Receive = {
    case commandMessage: CommandMessage =>
      log.debug(s"Received: $commandMessage")
      handleCommand(commandMessage)
  }

  def handleCommand: CommandMessage => Unit = {
    case cm @ CommandMessage(command: Cm) =>
      stateOpt.map(state => behavior.processCommand(state)(command))
        .orElse(behavior.processFirstCommand.lift(command))
        .map(handleReaction(cm))
        .getOrElse(unhandled(command))
    case other => unhandled(other.command)
  }

  def handleReaction(commandMessage: CommandMessage): AggregateReaction[Ev, Er] => Unit = {
    case Accept(evt) => accept(commandMessage)(evt)
    case Reject(err) => reject(commandMessage)(err)
    case Collaborate(f) =>
      context.become(awaitingCollaborationResult(commandMessage), discardOld = false)
      f(ec).map(CollaborationResult).pipeTo(self)
    case Ignore =>
  }

  def awaitingCollaborationResult(commandMessage: CommandMessage): Receive = {
    case CollaborationResult(reaction) =>
      unstashAll()
      context.unbecome()
      handleReaction(commandMessage)(reaction)
    case Failure(reason) => throw reason
    case _ => stash()
  }

  private def accept(command: CommandMessage)(event: Ev) {
    persist(EventMessage(event).causedBy(command)) { persisted =>
      log.info("Event persisted: {}", event)
      updateState(persisted)
      acknowledgeCommand(command)(persisted.event.right)
    }
  }

  private def reject(causedBy: CommandMessage)(err: Er): Unit = {
    acknowledgeCommand(causedBy)(err.left[Ev])
  }

  def updateState(persisted: EventMessage[Ev]) {
    val event = persisted.event
    val nextState = stateOpt.map(state => behavior.applyEvent(state)(event)).getOrElse(behavior.applyFirstEvent(event))
    stateOpt = Option(nextState)
    messageProcessed(persisted.metadata.causationId.get, event)
  }

  def toDomainEventMessage(persisted: EventMessage[Ev]): DomainEventMessage[Ev] =
    DomainEventMessage(persisted, AggregateSnapshotId(id, lastSequenceNr))

  def acknowledgeCommand(commandMessage: CommandMessage)(result: Er \/ Ev) = {
    val deliveryReceipt = commandMessage.deliveryReceipt(result)
    sender() ! deliveryReceipt
    log.debug(s"Delivery receipt (for received command) sent ($deliveryReceipt)")
  }


  override def handleDuplicated(m: CommandMessage, result: Ev): Unit = m match {
    case cm @ CommandMessage(command: Cm) => acknowledgeCommand(cm)(result.right[Er])
  }
}
