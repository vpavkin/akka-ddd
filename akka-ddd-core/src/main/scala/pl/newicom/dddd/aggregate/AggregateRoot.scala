package pl.newicom.dddd.aggregate

import akka.actor._
import akka.persistence._
import pl.newicom.dddd.actor.{BusinessEntityActorFactory, GracefulPassivation, PassivationConfig}
import pl.newicom.dddd.aggregate.error.AggregateRootNotInitializedException
import pl.newicom.dddd.eventhandling.EventHandler
import pl.newicom.dddd.messaging.command.CommandMessage
import pl.newicom.dddd.messaging.event.{AggregateSnapshotId, DomainEventMessage, EventMessage}
import pl.newicom.dddd.messaging.{Deduplication, Message}

import scala.concurrent.duration.{Duration, _}
import scala.util.{Failure, Success, Try}

trait AggregateState[T <: AggregateState[T]] { self: T =>
  type StateMachine = PartialFunction[DomainEvent, T]
  def apply: StateMachine
}

abstract class AggregateRootActorFactory[A <: AggregateRoot[_]] extends BusinessEntityActorFactory[A] {
  def props(pc: PassivationConfig): Props
  def inactivityTimeout: Duration = 1.minute
}

trait AggregateRoot[S <: AggregateState[S]]
  extends BusinessEntity with GracefulPassivation with PersistentActor
  with EventHandler with Deduplication with ActorLogging {

  type AggregateRootFactory = PartialFunction[DomainEvent, S]
  private var stateOpt: Option[S] = None
  private var _lastCommandMessage: Option[CommandMessage] = None
  private var _sender: ActorRef = null
  val factory: AggregateRootFactory

  override def persistenceId: String = id
  override def id = self.path.name


  override def receive: Receive = receiveDuplicate(commandDuplicated).orElse(receiveCommand)

  override def receiveCommand: Receive = {
    case cm: CommandMessage =>
      log.debug(s"Received: $cm")
      _lastCommandMessage = Some(cm)
      _sender = sender()
      handleCommand.applyOrElse(cm.command, unhandled)
  }

  override def receiveRecover: Receive = {
    case em: EventMessage =>
      updateState(em)
  }

  override def preRestart(reason: Throwable, msgOpt: Option[Any]) {
    acknowledgeCommandProcessed(commandMessage, Failure(reason))
    super.preRestart(reason, msgOpt)
  }

  /**
   * Command message being processed. Not available during recovery
   */
  def commandMessage = _lastCommandMessage.get

  def handleCommand: Receive

  def updateState(em: EventMessage) {
    val event = em.event
    val nextState = if (initialized) state.apply(event) else factory.apply(event)
    stateOpt = Option(nextState)
    messageProcessed(em)
  }

  def raise(event: DomainEvent) {
    persist(new EventMessage(event).causedBy(commandMessage)) {
      persisted =>
        {
          log.info("Event persisted: {}", event)
          updateState(persisted)
          handle(_sender, toDomainEventMessage(persisted))
        }
    }
  }

  def toDomainEventMessage(persisted: EventMessage) =
    new DomainEventMessage(persisted, AggregateSnapshotId(id, lastSequenceNr))
      .withMetaData(persisted.metadata)

  /**
   * Event handler, not invoked during recovery.
   */
  override def handle(senderRef: ActorRef, event: DomainEventMessage) {
    acknowledgeCommandProcessed(commandMessage)
  }

  def initialized = stateOpt.isDefined

  def state = if (initialized) stateOpt.get else throw new AggregateRootNotInitializedException

  def acknowledgeCommand(result: Any) = acknowledgeCommandProcessed(commandMessage, Success(result))

  def acknowledgeCommandProcessed(msg: Message, result: Try[Any] = Success("OK")) {
    val deliveryReceipt = msg.deliveryReceipt(result)
    _sender ! deliveryReceipt
    log.debug(s"Delivery receipt (for received command) sent ($deliveryReceipt)")
  }

  private def commandDuplicated(msg: Message) = acknowledgeCommandProcessed(msg)


}
