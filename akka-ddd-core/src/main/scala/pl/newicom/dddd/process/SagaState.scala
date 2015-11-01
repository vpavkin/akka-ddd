package pl.newicom.dddd.process

import akka.actor.ActorPath
import pl.newicom.dddd.aggregate.{Command, DomainEvent}
import pl.newicom.dddd.office.{Contract, OfficeInfo}
import pl.newicom.dddd.office.OfficeInfo.Aux
import shapeless._




trait TestOffice

case class OpenOffice(aggregateId: String) extends Command

case class OfficeOpened(aggregateId: String) extends DomainEvent


trait TOC extends Contract[TestOffice] {
  override type CommandImpl = OpenOffice
  override type ErrorImpl = String
  override type EventImpl = OfficeOpened
}

object TestOffice {
  implicit val state = new OfficeInfo[TestOffice] with TOC {
    override def name: String = "test"
  }
}

trait TestOffice2

case class OpenOffice2(aggregateId: String) extends Command

case class OfficeOpened2(aggregateId: String) extends DomainEvent

trait TOC2 extends Contract[TestOffice] {
  override type CommandImpl = OpenOffice2
  override type ErrorImpl = String
  override type EventImpl = OfficeOpened2
}

object TestOffice2 {
  implicit val state = new OfficeInfo[TestOffice2] with TOC2 {
    override def name: String = "test"
  }
}

trait OfficePath[O] {
  def value: ActorPath
}

object OfficePath {
  def apply[O](v: ActorPath) = new OfficePath[O] {
    override def value: ActorPath = v
  }
}

trait SagaState[S, C <: Coproduct] {

  sealed trait Reaction {
    def and(reaction: Reaction): Reaction = Compound(List(this, reaction))
  }
  case class ChangeState(state: S) extends Reaction
  case class SendCommand private [process](officeName: ActorPath, command: Command) extends Reaction
  case class Compound(reactions: List[Reaction]) extends Reaction
  case object DoNothing extends Reaction

  implicit def stateToChangeState(s: S): ChangeState = ChangeState(s)

  trait EventHandler[Ev] {
    def handleEvent(state: S): Function1[Ev, Reaction]
  }

  trait DeliverF[O] {
    def apply[Cm <: Command, Ev <: DomainEvent, Er](command: Cm)(implicit officeInfo: OfficeInfo.Aux[O, Cm, Ev, Er], eventHandler: Lazy[EventHandler[Ev]], path: OfficePath[O]): Reaction
  }

  def sendTo[O] = new DeliverF[O] {
    override def apply[Cm <: Command, Ev <: DomainEvent, Er](command: Cm)(implicit officeInfo: Aux[O, Cm, Ev, Er], eventHandler: Lazy[EventHandler[Ev]], path: OfficePath[O]): Reaction = SendCommand(path.value, command)
  }

  def applyEvent[E](s: S)(implicit eventHandler: EventHandler[E]): E => Reaction = eventHandler.handleEvent(s)
}

object SomeSaga extends SagaState[String, TOC :+: TOC2 :+: CNil] {

  implicit val testOffice2Path = OfficePath[TestOffice2](null)
  implicit val testOfficePath = OfficePath[TestOffice](null)

  implicit def fromOffice1 = new EventHandler[OfficeOpened] {
    override def handleEvent(state: String): (OfficeOpened) => SomeSaga.Reaction = { e: OfficeOpened  =>
      "ssa" and sendTo[TestOffice2](OpenOffice2(e.aggregateId))
    }
  }

  implicit def fromOffice2: EventHandler[OfficeOpened2] = new EventHandler[OfficeOpened2] {
    override def handleEvent(state: String): (OfficeOpened2) => SomeSaga.Reaction = { case OfficeOpened2(a) =>
      sendTo[TestOffice](OpenOffice(a))
    }
  }
}


