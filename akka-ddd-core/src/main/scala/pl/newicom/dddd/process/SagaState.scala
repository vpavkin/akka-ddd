package pl.newicom.dddd.process

import akka.actor.ActorPath
import pl.newicom.dddd.aggregate.{Command, DomainEvent}
import pl.newicom.dddd.office.{Contract, OfficeInfo}
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

trait InputHandler[A, S, In] {
  def handle(state: S): Function1[In, Reaction[S]]
}

sealed trait Reaction[+S] {
  def and[S1 >: S](reaction: Reaction[S1]): Reaction[S1] = Compound(List(this, reaction))
}
case class ChangeState[+S](state: S) extends Reaction[S]
case class SendCommand[O, Cmd <: Command, Evt <: DomainEvent, Err](officePath: OfficePath[O], command: Cmd)(implicit contract: Contract.Aux[O, Cmd, Evt, Err]) extends Reaction[Nothing]
case class Compound[+S](reactions: List[Reaction[S]]) extends Reaction[S]
case object DoNothing extends Reaction[Nothing]


trait SagaState[S, Evt <: Coproduct, Err <: Coproduct] {

  type L = Lifter[this.type, S, Evt]

  type IH[In] = InputHandler[this.type, S, In]

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

  val any2Cp: AnyInject[Evt]
}

trait AnyInject[C <: Coproduct] extends Serializable {
  def inject(a: Any): Option[C]
}

object AnyInject {
  implicit def ccons[H : Typeable, T <: Coproduct](implicit tcp: Lazy[AnyInject[T]]): AnyInject[H :+: T] = new AnyInject[H :+: T] {
    def inject(a: Any): Option[H :+: T] = {
      a.cast[H].map(Coproduct[H :+: T](_)).orElse(tcp.value.inject(a).map(_.extendLeft[H]))
    }
  }
  implicit val cnil: AnyInject[CNil] = new AnyInject[CNil] {
    override def inject(a: Any): Option[CNil] = None
  }
}

trait Lifter[F, S, C <: Coproduct] extends DepFn2[C, S] with Serializable { type Out = Reaction[S] }

object Lifter {
  implicit def cnilLifter[S, F]: Lifter[F, S, CNil] = new Lifter[F, S, CNil] {
    def apply(t: CNil, s: S): Out = DoNothing
  }

  implicit def cpLifter[F, S, H, T <: Coproduct]
  (implicit fh: InputHandler[F, S, H], mt: Lifter[F, S, T]): Lifter[F, S, H :+: T] =
    new Lifter[F, S, H :+: T] {
      def apply(c: H :+: T, s: S): Out = c match {
        case Inl(h) => fh.handle(s)(h)
        case Inr(t) => mt(t, s)
      }
    }
}



object SagaActor {
  def apply[S, Evt <: Coproduct](a0: SagaState[S, Evt])(implicit l: Lifter[a0.type, S, Evt]) = new SagaActor[S, Evt] {
    override val a: SagaState[S, Evt] = a0
    override implicit val lifter = l
  }
}

trait SagaActor[S, Evt <: Coproduct] {

  var s: S = ???

  val a: SagaState[S, Evt]

  implicit val lifter: Lifter[a.type, S, Evt]

  def receive(a: SagaState[S, Evt])(implicit lifter: Lifter[a.type, S, Evt]): Any => Unit = { in =>
    implicit val a2cp = a.any2Cp

    def applyEvent(s: S)(implicit lifter: Lifter[a.type, S, Evt]): Evt => Reaction[S] = {in => lifter(in, s)}

    def toCoproduct(a: Any): Option[Evt] = a2cp.inject(a)

    def maybeHandleInput(s: S)(in: Any)(implicit lifter: Lifter[a.type, S, Evt]): Option[Reaction[S]] = toCoproduct(in).map(applyEvent(s))
    maybeHandleInput(s)(in) match {
      case Some(reaction) => runReaction(reaction)
      case None => unhandled(in)
    }
  }


  def runReaction(reaction: Reaction[S]): Unit = ???

  def unhandled(a: Any): Unit = ???
}

object SomeSaga extends SagaState[String, OfficeOpened :+: OfficeOpened2 :+: CNil, String :+: CNil] {

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

  override val any2Cp: AnyInject[:+:[TOC, :+:[TOC2, CNil]]] = implicitly
}


