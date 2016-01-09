package pl.newicom.dddd.test.dummy

import java.util.UUID

import pl.newicom.dddd.actor.PassivationConfig
import pl.newicom.dddd.aggregate._
import pl.newicom.dddd.cluster.DefaultShardResolution
import pl.newicom.dddd.eventhandling.EventPublisher
import pl.newicom.dddd.office.{OfficeContract, OfficeInfo}
import pl.newicom.dddd.utils.UUIDSupport

object DummyAggregateRoot {

  //
  // Commands
  //

  trait DummyOfficeContract extends OfficeContract[DummyOffice] {
    override type CommandImpl = DummyCommand
    override type ErrorImpl = String
    override type EventImpl = DummyEvent
  }


  trait DummyOffice

  object DummyOffice {
    implicit val info = new OfficeInfo[DummyOffice] {
      override def name: String = "Dummy"
    }

    implicit val tc: OfficeContract.Aux[DummyOffice, DummyCommand, DummyEvent, String] = new OfficeContract[DummyOffice] {
      override type CommandImpl = DummyCommand
      override type ErrorImpl = String
      override type EventImpl = DummyEvent
    }
    implicit def defaultShardResolution = new DefaultShardResolution[DummyOffice]
  }

  sealed trait DummyCommand extends Command {
    def id: EntityId
    override def aggregateId: String = id
  }

  case class CreateDummy(id: EntityId, name: String, description: String, value: Int) extends DummyCommand
  case class ChangeName(id: EntityId, name: String) extends DummyCommand
  case class ChangeDescription(id: EntityId, description: String) extends DummyCommand
  case class ChangeValue(id: EntityId, value: Int) extends DummyCommand
  case class GenerateValue(id: EntityId) extends DummyCommand
  case class ConfirmGeneratedValue(id: EntityId, confirmationToken: UUID) extends DummyCommand

  //
  // Events
  //
  sealed trait DummyEvent extends DomainEvent {
    def id: EntityId

    override def aggregateId: EntityId = id
  }
  case class DummyCreated(id: EntityId, name: String, description: String, value: Int) extends DummyEvent
  case class NameChanged(id: EntityId, name: String) extends DummyEvent
  case class DescriptionChanged(id: EntityId, description: String) extends DummyEvent
  case class ValueChanged(id: EntityId, value: Int, dummyVersion: Long) extends DummyEvent
  case class ValueGenerated(id: EntityId, value: Int, confirmationToken: UUID) extends DummyEvent

  case class CandidateValue(value: Int, confirmationToken: UUID)


  case class DummyState(value: Int, candidateValue: Option[CandidateValue], version: Int) {
    def bumpVersion: DummyState = copy(version = version + 1)
  }

  object DummyBehavior extends AggregateRootBehavior[DummyState, DummyCommand, DummyEvent, String] with UUIDSupport {
    type State = DummyState
    override def processFirstCommand: ProcessFirstCommand = {
      case CreateDummy(id, name, description, value) =>
        if (value < 0) {
          reject("negative value not allowed")
        } else {
          accept(DummyCreated(id, name, description, value))
        }
      case _ => reject("Unknown dummy")
    }

    override def applyFirstEvent: ApplyFirstEvent = {
      case DummyCreated(_, _, _, value) => DummyState(value, None, 0)
    }

    override def processCommand(state: DummyState): ProcessCommand = {
      case CreateDummy(id, name, description, value) => reject("Dummy already exists")

      case ChangeName(id, name) => accept(NameChanged(id, name))

      case ChangeDescription(id, description) => accept(DescriptionChanged(id, description))


      case ChangeValue(id, value) => if (value < 0) {
        reject("negative value not allowed")
      } else {
        accept(ValueChanged(id, value, state.version + 1))
      }

      case GenerateValue(id) =>
        val value = (Math.random() * 100).toInt
        accept(ValueGenerated(id, value, confirmationToken = uuidObj))

      case ConfirmGeneratedValue(id, confirmationToken) =>
        candidateValue(state)(confirmationToken).map { value =>
          accept(ValueChanged(id, value, state.version + 1))
        } getOrElse {
          reject("Not found")
        }
    }

    override def applyEvent(state: DummyState): ApplyEvent = { e: DummyEvent => e match {
      case ValueChanged(_, newValue, _) =>
        state.copy(value = newValue, candidateValue = None)
      case ValueGenerated(_, newValue, confirmationToken) =>
        state.copy(candidateValue = Some(CandidateValue(newValue, confirmationToken)))
      case _ => state
    } } andThen(_.bumpVersion)

    def candidateValue(state: DummyState)(confirmationToken: UUID): Option[Int] = {
      state.candidateValue.flatMap { cv =>
        if (cv.confirmationToken == confirmationToken) Some(cv.value) else None
      }
    }
  }
}

import DummyAggregateRoot._

class DummyAggregateRoot extends AggregateRootActor[DummyOffice, DummyState, DummyCommand, DummyEvent, String](PassivationConfig(), DummyBehavior) { this: EventPublisher[DummyEvent] =>
}