package pl.newicom.dddd.test.dummy

import java.util.UUID

import akka.actor.Props
import pl.newicom.dddd.actor.PassivationConfig
import pl.newicom.dddd.aggregate
import pl.newicom.dddd.aggregate.{Command, AggregateRoot, AggregateState, EntityId}
import pl.newicom.dddd.eventhandling.EventPublisher
import pl.newicom.dddd.messaging.CollaborationSupport
import pl.newicom.dddd.office.OfficeInfo
import pl.newicom.dddd.test.dummy.DummyAggregateRoot._
import pl.newicom.dddd.test.dummy.ValueGenerator.GenerateRandom
import pl.newicom.dddd.utils.UUIDSupport.uuidObj

object DummyAggregateRoot {

  //
  // Commands
  //

  trait DummyOffice

  object DummyOffice {
    implicit val info = OfficeInfo[DummyOffice]("Dummy")
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
  sealed trait DummyEvent {
    def id: EntityId
  }
  case class DummyCreated(id: EntityId, name: String, description: String, value: Int) extends DummyEvent
  case class NameChanged(id: EntityId, name: String) extends DummyEvent
  case class DescriptionChanged(id: EntityId, description: String) extends DummyEvent
  case class ValueChanged(id: EntityId, value: Int, dummyVersion: Long) extends DummyEvent
  case class ValueGenerated(id: EntityId, value: Int, confirmationToken: UUID) extends DummyEvent

  case class CandidateValue(value: Int, confirmationToken: UUID)


  case class DummyState(value: Int, candidateValue: Option[CandidateValue] = None)

    object DummyState {
      implicit val instance =  new AggregateState[DummyState] {


        override type CommandImpl = DummyCommand
        override type ErrorImpl = String
        override type EventImpl = DummyEvent

        override def applyEvent(state: DummyState): ApplyEvent = {
          case ValueChanged(_, newValue, _) =>
            state.copy(value = newValue, candidateValue = None)
          case ValueGenerated(_, newValue, confirmationToken) =>
            state.copy(candidateValue = Some(CandidateValue(newValue, confirmationToken)))
          case _ => state
        }

        override def initiate: Initiate = {
          case CreateDummy(id, name, description, value) =>
              if (value < 0) {
                reject("negative value not allowed")
              } else {
                raise(DummyCreated(id, name, description, value))
              }
          case _ => reject("Unknown dummy")
        }

        override def handleInitiated: HandleInitiated = {
          case DummyCreated(_, _, _, value) => DummyState(value)
        }

        override def processCommand(state: DummyState): ProcessCommand = {
          case CreateDummy(id, name, description, value) => reject("Dummy already exists")

          case ChangeName(id, name) => raise(NameChanged(id, name))

          case ChangeDescription(id, description) => raise(DescriptionChanged(id, description))


          case ChangeValue(id, value) => if (value < 0) {
            reject("negative value not allowed")
          } else {
            raise(ValueChanged(id, value, 0L))
          }

          case GenerateValue(id) =>
            val value = (Math.random() * 100).toInt
            raise(ValueGenerated(id, value, confirmationToken = uuidObj))

          case ConfirmGeneratedValue(id, confirmationToken) =>
            candidateValue(state)(confirmationToken).map { value =>
              raise(ValueChanged(id, value, 0L))
            } getOrElse {
              reject("Not found")
            }
        }

        def candidateValue(state: DummyState)(confirmationToken: UUID): Option[Int] = {
          state.candidateValue.flatMap { cv =>
            if (cv.confirmationToken == confirmationToken) Some(cv.value) else None
          }
        }
      }
    }
}

class DummyAggregateRoot extends AggregateRoot[DummyState, DummyOffice, DummyCommand, DummyEvent, String] { this: EventPublisher[DummyEvent] =>
  override val pc = PassivationConfig()
}