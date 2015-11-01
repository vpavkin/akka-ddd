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
import pl.newicom.dddd.office.OfficeInfo

import scala.reflect.ClassTag
import scalaz.\/
import scalaz.Scalaz._
import scala.concurrent.duration.{Duration, _}
import scala.util.{Failure, Success, Try}


trait AggregateRoot[State, Office] {
  type EventImpl <: aggregate.DomainEvent
  type CommandImpl <: Command
  type ErrorImpl

  type CommandProcessingResult = ErrorImpl \/ EventImpl
  type ProcessCommand = CommandImpl => CommandProcessingResult
  type ApplyEvent = EventImpl => State
  type ProcessFirstCommand = PartialFunction[CommandImpl, CommandProcessingResult]
  type ApplyFirstEvent = PartialFunction[EventImpl, State]

  def processCommand(state: State): ProcessCommand
  def applyEvent(state: State): ApplyEvent
  def processFirstCommand: ProcessFirstCommand
  def applyFirstEvent: ApplyFirstEvent

  def raise(e: EventImpl): CommandProcessingResult = e.right
  def reject(e: ErrorImpl): CommandProcessingResult = e.left
}

object AggregateRoot {
  type Aux[S, O, CommandImpl0 <: Command, EventImpl0 <: aggregate.DomainEvent, ErrorImpl0] = AggregateRoot[S, O] {
    type EventImpl = EventImpl0
    type CommandImpl = CommandImpl0
    type ErrorImpl = ErrorImpl0
  }
}

abstract class AggregateRootActorFactory[A <: AggregateRootActor[_, _, _, _, _]]
  extends BusinessEntityActorFactory[A] {
  def props(pc: PassivationConfig): Props
  def inactivityTimeout: Duration = 1.minute
}

abstract class AggregateRootActor[S, O, Cm <: Command, Ev <: DomainEvent, Er](implicit as: AggregateRoot.Aux[S, O, Cm, Ev, Er], officeInfo: OfficeInfo[O], ev: ClassTag[Ev], cm: ClassTag[Cm])
  extends BusinessEntity with GracefulPassivation with PersistentActor
  with EventHandler[Ev] with Deduplication with ActorLogging {



  private var stateOpt: Option[S] = None
  private var _lastCommandMessage: Option[CommandMessage] = None

  override def persistenceId: String = s"${officeInfo.name}-$id"
  override def id = self.path.name

  override def receiveRecover = {
    case em: EventMessage[Ev] => updateState(em)
  }

  override def preRestart(reason: Throwable, msgOpt: Option[Any]) {
    acknowledgeCommandProcessed(commandMessage, Failure(reason))
    super.preRestart(reason, msgOpt)
  }

  override def receive: Receive = receiveDuplicate(commandDuplicated).orElse(receiveCommand)

  override def receiveCommand: Receive = {
    case commandMessage: CommandMessage =>
      log.debug(s"Received: $commandMessage")
      _lastCommandMessage = Some(commandMessage)
      commandMessage.command match {
        case command: Cm => handleCommand(command)
        case other => unhandled(other)
      }
  }

  def commandMessage = _lastCommandMessage.get

  def handleCommand: Cm => Unit = { command =>
    val processingResult = initialized ? as.processCommand(state)(command) | as.processFirstCommand(command)
    processingResult.map { event =>
      raise(event)
    }.leftMap(_.left[Ev]).valueOr(acknowledgeCommand)
  }


  private def raise(event: Ev) {
    persist(new EventMessage(event).causedBy(commandMessage)) { persisted =>
      log.info("Event persisted: {}", event)
      updateState(persisted)
      handle(sender(), toDomainEventMessage(persisted))
    }
  }

  def updateState(persisted: EventMessage[Ev]) {
    val event = persisted.event
    val nextState = if (initialized) as.applyEvent(state)(event) else as.applyFirstEvent(event)
    stateOpt = Option(nextState)
    messageProcessed(persisted)
  }

  def toDomainEventMessage(persisted: EventMessage[Ev]): DomainEventMessage[Ev] =
    new DomainEventMessage(persisted, AggregateSnapshotId(id, lastSequenceNr))
      .withMetaData(persisted.metadata)

  override def handle(senderRef: ActorRef, eventMessage: DomainEventMessage[Ev]) {
    acknowledgeCommandProcessed(commandMessage, Success(eventMessage.event.right[Er]))
  }

  def initialized = stateOpt.isDefined

  def state = if (initialized) stateOpt.get else throw new AggregateRootNotInitializedException

  def acknowledgeCommand(result: Er \/ Ev) = acknowledgeCommandProcessed(commandMessage, Success(result))

  def acknowledgeCommandProcessed(msg: Message, result: Try[Any] = Success("Ok")) {
    val deliveryReceipt = msg.deliveryReceipt(result)
    sender() ! deliveryReceipt
    log.debug(s"Delivery receipt (for received command) sent ($deliveryReceipt)")
  }

  private def commandDuplicated(msg: Message) = acknowledgeCommandProcessed(msg)

  val pc: PassivationConfig
}
