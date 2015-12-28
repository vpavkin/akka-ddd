package pl.newicom.dddd.test.dummy

import akka.actor.{ActorPath, Props}
import pl.newicom.dddd.actor.PassivationConfig
import pl.newicom.dddd.aggregate._
import pl.newicom.dddd.messaging.correlation.EntityIdResolution
import pl.newicom.dddd.messaging.event.EventMessage
import pl.newicom.dddd.office.{OfficePath, OfficeInfo}
import pl.newicom.dddd.process.EventDecision.Accept
import pl.newicom.dddd.process._
import pl.newicom.dddd.test.dummy.DummyAggregateRoot.{DummyOffice, DummyCreated, ValueChanged}
import pl.newicom.dddd.test.dummy.DummySaga.{EventApplied, DummyCommand}
import shapeless.ops.coproduct.Mapper.Aux
import shapeless.{Poly1, :+:, CNil}
object DummySaga {

  implicit def defaultSagaIdResolution[A]: EntityIdResolution[A] = new EntityIdResolution[A]

  implicit object DummySagaActorFactory extends SagaActorFactory[DummySaga] {
    override def props(pc: PassivationConfig): Props = {
      Props(new DummySaga(pc, None))
    }
  }

  class DummySagaConfig(bpsName: String) extends SagaConfig[DummySaga](bpsName) {
    def correlationIdResolver = {
      case ValueChanged(pId, _, _) => pId
      case DummyCreated(pId, _, _, _) => pId
      case other => throw new scala.RuntimeException(s"unknown event: ${other.getClass.getName}")
    }
  }

  implicit val config = new DummySagaConfig("DummySaga")

  case class DummyCommand(processId: EntityId, value: Int) extends Command {
    override def aggregateId: String = processId
  }

  case class EventApplied(e: DomainEvent)
}

/**
 * <code>DummySaga</code> keeps a <code>counter</code> that is bumped whenever
 * <code>DummyEvent</code> is received containing <code>value</code> equal to <code>counter + 1</code>
 * <code>DummySaga</code> publishes all applied events to local actor system bus.
 */


class DummySaga(dummyOffice: Option[OfficePath[DummyOffice]]) extends SagaConfig {



  override def name: String = "DummySaga"
  override type Input = DummyCreated :+: ValueChanged :+: CNil
  override type State = Int
  object resolveId extends ResolveId {
    implicit val atDC = at[DummyCreated](_.id)
    implicit val atVC = at[ValueChanged](_.id)
  }


  def applyEvent = {
    case e @ ValueChanged(id, value, _) =>
      dummyOffice.foreach(_ !! DummyCommand(id, counter))

  }


  object applyEvent extends ApplyEvent[State] {
    implicit val atVC = at[ValueChanged] { case ValueChanged(id, value, _) => state =>
      changeState(value) and (dummyOffice.map(_ !! DummyCommand(id, state.getOrElse(0))).getOrElse(ignore))
    }
  }

  object receiveEvent extends ReceiveEvent[State] {
    implicit val atVC = at[ValueChanged] { case (state, e) => if (state.map(_ + 1).contains(e.value)) accept else ignore }
    implicit val atDC = at[DummyCreated] { case (state, e) => if (state.isEmpty) accept else ignore }
  }

  override implicit def mapper: Aux[receiveEvent.type, :+:[ValueChanged, CNil], :+:[Unit, CNil]] = ???
}
