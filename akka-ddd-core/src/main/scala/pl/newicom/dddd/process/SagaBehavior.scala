package pl.newicom.dddd.process

import akka.actor.ActorPath
import pl.newicom.dddd.aggregate.{Command, DomainEvent}
import pl.newicom.dddd.office.{Contract, OfficeInfo}
import pl.newicom.dddd.process.SagaBehavior.Aux
import shapeless._
import shapeless.syntax.typeable._


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

trait TOC2 extends Contract[TestOffice2] {
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

trait InputHandler[B, S, In] {
  def handle(state: S): In => Reaction[S]
}

object InputHandler {
  implicit def cnil[B, S]: InputHandler[B, S, CNil] = new InputHandler[B, S, CNil] {
    def handle(s: S) = Function.const(DoNothing)
  }

  implicit def ccons[B, S, H, T <: Coproduct]
  (implicit
   fh: InputHandler[B, S, H],
   mt: InputHandler[B, S, T]): InputHandler[B, S, H :+: T] =
    new InputHandler[B, S, H :+: T] {
      def handle(s: S) =  {
        case Inl(h) => fh.handle(s)(h)
        case Inr(t) => mt.handle(s)(t)
      }
    }
}

sealed trait Reaction[+S] {
  def and[S1 >: S](reaction: Reaction[S1]): Reaction[S1] = And(this, reaction)
}
case class ChangeState[+S](state: S) extends Reaction[S]
case class SendCommand[O, Cmd <: Command, Evt <: DomainEvent, Err](officePath: OfficePath[O], command: Cmd)(implicit contract: Contract.Aux[O, Cmd, Evt, Err]) extends Reaction[Nothing]
case class And[+S](first: Reaction[S], second: Reaction[S]) extends Reaction[S]
case object DoNothing extends Reaction[Nothing]


trait SagaBehavior[Saga, Self <: SagaBehavior[Saga, Self]] { this: Self =>

  type S

  type Evt <: Coproduct

  type Err <: Coproduct

  type IH[In] = InputHandler[Self, S, In]

  def handle[In](f: S => In => Reaction[S]): IH[In] = new IH[In] {
    override def handle(state: S): (In) => Reaction[S] = f(state)
  }

  implicit def stateToChangeState(s: S): Reaction[S] = ChangeState(s)

  trait DeliverF[O] {
    def apply[Cm <: Command, Ev <: DomainEvent, Er](command: Cm)(implicit officeInfo: OfficeInfo.Aux[O, Cm, Ev, Er], eventHandler: IH[Ev], errorHandler: IH[Er], path: OfficePath[O]): Reaction[S]
  }

  def sendTo[O] = new DeliverF[O] {
    override def apply[Cm <: Command, Ev <: DomainEvent, Er](command: Cm)(implicit officeInfo: OfficeInfo.Aux[O, Cm, Ev, Er], eventHandler: IH[Ev], errorHandler: IH[Er], path: OfficePath[O]): Reaction[S] = SendCommand(path, command)
  }
}

object SagaBehavior {
  type Aux[Saga, State0, Evt0 <: Coproduct, Err0 <: Coproduct, Self <: SagaBehavior[Saga, Self]] = SagaBehavior[Saga, Self] {
    type State = State0
    type Evt = Evt0
    type Err = Err0
  }
}

trait AnyInject[C <: Coproduct] extends Serializable {
  def apply(a: Any): Option[C]
}

object AnyInject {
  implicit def ccons[H : Typeable, T <: Coproduct](implicit tcp: Lazy[AnyInject[T]]): AnyInject[H :+: T] = new AnyInject[H :+: T] {
    def apply(a: Any): Option[H :+: T] = {
      a.cast[H].map(Coproduct[H :+: T](_)).orElse(tcp.value(a).map(_.extendLeft[H]))
    }
  }
  implicit val cnil: AnyInject[CNil] = new AnyInject[CNil] {
    override def apply(a: Any): Option[CNil] = None
  }
}


object SagaActor {
  trait Create[Saga] {
    def apply[S, Evt <: Coproduct, Err <: Coproduct, Self <: SagaBehavior[Saga, Self]]()(implicit a0: SagaBehavior.Aux[Saga, S, Evt, Err, Self], handler: InputHandler[Self, S, Evt], inject: AnyInject[Evt]) = new SagaActor
  }

  def create[Saga] = new Create[Saga] {

  }
}

class SagaActor[S, Evt <: Coproduct, Err <: Coproduct, Saga, Self <: SagaBehavior[Saga, Self]](implicit a0: SagaBehavior.Aux[Saga, S, Evt, Err, Self], handler: InputHandler[Self, S, Evt], inject: AnyInject[Evt]) {

  var s: S = ???

  def receive: Any => Unit = { in =>

    def maybeHandleInput(s: S)(in: Any): Option[Reaction[S]] = inject(in).map(handler.handle(s))

    maybeHandleInput(s)(in) match {
      case Some(reaction) => runReaction(reaction)
      case None => unhandled(in)
    }
  }

  def runReaction(reaction: Reaction[S]): Unit = ???

  def unhandled(a: Any): Unit = ???
}

trait SomeSaga

object SomeSaga {
  implicit def behavior: SagaBehavior[SomeSaga, SomeSagaBehavior] = new SomeSagaBehavior
}

class SomeSagaBehavior extends SagaBehavior[SomeSaga, SomeSagaBehavior] {
  override type S = String
  override type Evt = OfficeOpened :+: OfficeOpened2 :+: CNil
  override type Err = String :+: CNil

  implicit val testOffice2Path = OfficePath[TestOffice2](null)
  implicit val testOfficePath = OfficePath[TestOffice](null)

  implicit def eventsFromOffice1 = handle[OfficeOpened] { state => {
    e: OfficeOpened => "ssa" and sendTo[TestOffice2](OpenOffice2(e.aggregateId))
  }}

  implicit def errors: IH[String] = handle[String] { state => {
    case error => DoNothing
  }}

  implicit def eventsFromOffice2: IH[OfficeOpened2] = handle[OfficeOpened2] { state => {
    case OfficeOpened2(a) => sendTo[TestOffice](OpenOffice(a))
  }}
}

object Test {
  SagaActor.create[SomeSaga]
}