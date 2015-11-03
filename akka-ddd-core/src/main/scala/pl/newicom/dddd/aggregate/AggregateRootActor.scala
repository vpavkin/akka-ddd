package pl.newicom.dddd.aggregate

import akka.actor._
import akka.persistence._
import pl.newicom.dddd.actor.{BusinessEntityActorFactory, GracefulPassivation, PassivationConfig}
import pl.newicom.dddd.aggregate
import pl.newicom.dddd.aggregate.error.AggregateRootNotInitializedException
import pl.newicom.dddd.eventhandling.{EventPublisher, EventHandler}
import pl.newicom.dddd.messaging.command.CommandMessage
import pl.newicom.dddd.messaging.event.{AggregateSnapshotId, DomainEventMessage, EventMessage}
import pl.newicom.dddd.messaging.{Deduplication, Message}
import pl.newicom.dddd.office.{Contract, OfficeInfo}

import scala.reflect.ClassTag
import scalaz.\/
import scalaz.Scalaz._
import scala.concurrent.duration.{Duration, _}
import scala.util.{Failure, Success, Try}


trait AggregateRoot[State, Office] extends Contract[Office] {

  type CommandProcessingResult = ErrorImpl \/ EventImpl
  type ProcessCommand = CommandImpl => CommandProcessingResult
  type ApplyEvent = EventImpl => State
  type ProcessFirstCommand = PartialFunction[CommandImpl, CommandProcessingResult]
  type ApplyFirstEvent = PartialFunction[EventImpl, State]

  def processCommand(state: State): ProcessCommand
  def applyEvent(state: State): ApplyEvent
  def processFirstCommand: ProcessFirstCommand
  def applyFirstEvent: ApplyFirstEvent

  def accept(e: EventImpl): CommandProcessingResult = e.right
  def reject(e: ErrorImpl): CommandProcessingResult = e.left
}

object AggregateRoot {
  type Aux[S, O, CommandImpl0 <: Command, EventImpl0 <: aggregate.DomainEvent, ErrorImpl0] = AggregateRoot[S, O] {
    type EventImpl = EventImpl0
    type CommandImpl = CommandImpl0
    type ErrorImpl = ErrorImpl0
  }
}

abstract class AggregateRootActorFactory[A] extends BusinessEntityActorFactory[A] {
  def props(pc: PassivationConfig): Props
  def inactivityTimeout: Duration = 1.minute
}

abstract class AggregateRootActor[S, O, Cm <: Command, Ev <: DomainEvent, Er](implicit behavior: AggregateRoot.Aux[S, O, Cm, Ev, Er], officeInfo: OfficeInfo[O], ev: ClassTag[Ev], cm: ClassTag[Cm])
  extends BusinessEntity with GracefulPassivation with PersistentActor
  with EventHandler[Ev] with Deduplication with ActorLogging {

  private var stateOpt: Option[S] = None
  private var lastCommandMessage: Option[CommandMessage] = None

  override def persistenceId: String = s"${officeInfo.name}-$id"
  override def id = self.path.name

  override def receiveRecover = {
    case em: EventMessage[Ev] => updateState(em)
  }

  override def receive: Receive = receiveDuplicate(commandDuplicated).orElse(receiveCommand)

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
    stateOpt
      .map(state => behavior.processCommand(state)(command))
      .getOrElse(behavior.processFirstCommand(command))
      .map(raise(commandMessage))
      .leftMap(_.left[Ev])
      .valueOr(acknowledgeCommand(commandMessage))
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

  private def commandDuplicated(msg: Message) = acknowledgeCommandProcessed()(msg)

  val pc: PassivationConfig
}
