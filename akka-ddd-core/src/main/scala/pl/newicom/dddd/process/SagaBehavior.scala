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

trait NotificationOffice

case class Notify(aggregateId: String) extends Command

case class Notified(aggregateId: String) extends DomainEvent

trait TOC2 extends Contract[NotificationOffice] {
  override type CommandImpl = Notify
  override type ErrorImpl = String
  override type EventImpl = Notified
}

object NotificationOffice {
  implicit val state = new OfficeInfo[NotificationOffice] with TOC2 {
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

trait Handler[B, S, In] {
  def handle(state: S): In => Reaction[S]
}

object Handler {
  implicit def cnil[B, S]: Handler[B, S, CNil] = new Handler[B, S, CNil] {
    def handle(s: S) = Function.const(DoNothing)
  }

  implicit def ccons[B, S, H, T <: Coproduct]
  (implicit
   fh: Handler[B, S, H],
   mt: Handler[B, S, T]): Handler[B, S, H :+: T] =
    new Handler[B, S, H :+: T] {
      def handle(s: S) =  {
        case Inl(h) => fh.handle(s)(h)
        case Inr(t) => mt.handle(s)(t)
      }
    }
}

trait Init[B, S, In] {
  def init: In => Reaction[S]
}

object Init {
  implicit def cnil[B, S]: Init[B, S, CNil] = new Init[B, S, CNil] {
    def init = Function.const(DoNothing)
  }

  implicit def ccons[B, S, H, T <: Coproduct]
  (implicit
   fh: Init[B, S, H],
   mt: Init[B, S, T]): Init[B, S, H :+: T] =
    new Init[B, S, H :+: T] {
      def init = {
        case Inl(h) => fh.init(h)
        case Inr(t) => mt.init(t)
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


trait SagaBehavior[Saga, Self <: SagaBehavior[Saga, Self]] { self: Self =>

  type S

  type Evt <: Coproduct

  type H[In] = Handler[Self, S, In]
  type I[In] = Init[Self, S, In]

  def handle[In](f: S => In => Reaction[S]): H[In] = new H[In] {
    override def handle(state: S): (In) => Reaction[S] = f(state)
  }

  def init[In](f: In => Reaction[S]): I[In] = new I[In] {
    override def init: (In) => Reaction[S] = f
  }

  trait SendTo[O] {
    def apply[Cm <: Command, Ev <: DomainEvent, Er](command: Cm)(implicit officeInfo: OfficeInfo.Aux[O, Cm, Ev, Er], eventHandler: H[Ev], errorHandler: H[Er], path: OfficePath[O]): Reaction[S]
  }

  def sendTo[O] = new SendTo[O] {
    override def apply[Cm <: Command, Ev <: DomainEvent, Er](command: Cm)(implicit officeInfo: OfficeInfo.Aux[O, Cm, Ev, Er], eventHandler: H[Ev], errorHandler: H[Er], path: OfficePath[O]): Reaction[S] = SendCommand(path, command)
  }

  def changeState(newState: S): Reaction[S] = ChangeState(newState)
}

object SagaBehavior {
  type Aux[Saga, State0, Evt0 <: Coproduct, Self <: SagaBehavior[Saga, Self]] = SagaBehavior[Saga, Self] {
    type State = State0
    type Evt = Evt0
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
    def apply[State, In <: Coproduct, Self <: SagaBehavior.Aux[Saga, State, In, Self]]()(implicit a0: SagaBehavior.Aux[Saga, State, In, Self], handler: Handler[Self, State, In], inject: AnyInject[In]): SagaActor[State, In, Saga, Self]
  }

  def create[Saga] = new Create[Saga] {
    def apply[State, In <: Coproduct, Self <: SagaBehavior.Aux[Saga, State, In, Self]]()(implicit a0: SagaBehavior.Aux[Saga, State, In, Self], handler: Handler[Self, State, In], inject: AnyInject[In]): SagaActor[State, In, Saga, Self] = new SagaActor
  }
}

class SagaActor[State, In <: Coproduct, Saga, Self](implicit handler: Handler[Self, State, In], inject: AnyInject[In]) {

  var s: State = ???

  def handleInput(s: State)(in: Any): Option[Reaction[State]] = inject(in).map(handler.handle(s))

  def receive: Any => Unit = { in =>
    handleInput(s)(in) match {
      case Some(reaction) => runReaction(reaction)
      case None => unhandled(in)
    }
  }

  def runReaction(reaction: Reaction[State]): Unit = ???

  def unhandled(a: Any): Unit = ???
}

trait SomeSaga

object SomeSaga {
  def behavior[Self <: SagaBehavior.Aux[Saga, String, OfficeOpened :+: Notified :+: CNil, Self]](implicit testOffice2: OfficePath[NotificationOffice], testOffice: OfficePath[TestOffice]): SagaBehavior.Aux[SomeSaga,String, OfficeOpened :+: Notified :+: CNil, Self] = new SomeSagaBehavior
}

class SomeSagaBehavior(implicit testOffice2: OfficePath[NotificationOffice], testOffice: OfficePath[TestOffice]) extends SagaBehavior[SomeSaga, SomeSagaBehavior] {
  override type S = String
  override type Evt = OfficeOpened :+: Notified :+: CNil

  implicit def eventsFromOffice1 = handle[OfficeOpened] { state => {
    e: OfficeOpened => changeState("opened") and sendTo[NotificationOffice](Notify(e.aggregateId))
  }}

  implicit def errors: H[String] = handle[String] { state => {
    case error => DoNothing
  }}

  implicit def eventsFromOffice2: H[Notified] = handle[Notified] { state => {
    case Notified(a) => sendTo[TestOffice](OpenOffice(a))
  }}
}

object Test {
  implicit val behavior = SomeSaga.behavior(OfficePath[NotificationOffice](null), OfficePath[TestOffice](null))
  SagaActor.create[SomeSaga]()
}