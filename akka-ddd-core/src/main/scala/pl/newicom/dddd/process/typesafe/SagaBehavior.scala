package pl.newicom.dddd.process.typesafe

import pl.newicom.dddd.aggregate._
import pl.newicom.dddd.office.{OfficePath, OfficeContract, OfficeInfo}
import shapeless._

sealed trait SagaEventDecision
object SagaEventDecision {
  case object Accept extends SagaEventDecision
  case object Ignore extends SagaEventDecision
}

trait ReceiveFirst[B, In] {
  def apply: In => SagaEventDecision
}

object ReceiveFirst {
  implicit def cnil[B]: ReceiveFirst[B, CNil] = new ReceiveFirst[B, CNil] {
    def apply = Function.const(SagaEventDecision.Ignore)
  }

  implicit def ccons[B, H, T <: Coproduct]
  (implicit
   fh: ReceiveFirst[B, H],
   mt: ReceiveFirst[B, T]): ReceiveFirst[B, H :+: T] =
    new ReceiveFirst[B, H :+: T] {
      def apply = {
        case Inl(h) => fh.apply(h)
        case Inr(t) => mt.apply(t)
      }
    }
}

trait Receive[B, S, In] {
  def apply(state: S): In => SagaEventDecision
}

object Receive {
  implicit def cnil[B, S]: Receive[B, S, CNil] = new Receive[B, S, CNil] {
    def apply(s: S) = Function.const(SagaEventDecision.Ignore)
  }

  implicit def ccons[B, S, H, T <: Coproduct]
  (implicit
   fh: Receive[B, S, H],
   mt: Receive[B, S, T]): Receive[B, S, H :+: T] =
    new Receive[B, S, H :+: T] {
      def apply(s: S) = {
        case Inl(h) => fh(s)(h)
        case Inr(t) => mt(s)(t)
      }
    }
}

trait ApplyFirst[B, S, In] {
  def apply: In => Reaction[S]
}

object ApplyFirst {
  implicit def cnil[B, S]: ApplyFirst[B, S, CNil] = new ApplyFirst[B, S, CNil] {
    def apply = Function.const(Ignore)
  }

  implicit def ccons[B, S, H, T <: Coproduct]
  (implicit
   fh: ApplyFirst[B, S, H],
   mt: ApplyFirst[B, S, T]): ApplyFirst[B, S, H :+: T] =
    new ApplyFirst[B, S, H :+: T] {
      def apply = {
        case Inl(h) => fh.apply(h)
        case Inr(t) => mt.apply(t)
      }
    }
}

trait Apply[B, S, In] {
  def apply(state: S): In => Reaction[S]
}

object Apply {
  implicit def cnil[B, S]: Apply[B, S, CNil] = new Apply[B, S, CNil] {
    def apply(s: S) = Function.const(Ignore)
  }

  implicit def ccons[B, S, H, T <: Coproduct]
  (implicit
   fh: Apply[B, S, H],
   mt: Apply[B, S, T]): Apply[B, S, H :+: T] =
    new Apply[B, S, H :+: T] {
      def apply(s: S) =  {
        case Inl(h) => fh(s)(h)
        case Inr(t) => mt(s)(t)
      }
    }
}

trait Handle[B, State, Event <: DomainEvent] {
  def apply(state: State)
}

trait SagaBehavior[Saga, State, In <: Coproduct, C <: Coproduct] extends Reactions[State, In, C] {

  type ApplyFirst0[In0] = ApplyFirst[this.type, State, In0]


  def apply[In0](f: State => In0 => Reaction[State]): Apply0[In0] = new Apply0[In0] {
    override def apply(state: State): (In0) => Reaction[State] = f(state)
  }

  def applyFirst[In0](f: In0 => Reaction[State]): ApplyFirst0[In0] = new ApplyFirst0[In0] {
    override def apply: (In0) => Reaction[State] = f
  }
}

trait Reactions[State, In <: Coproduct, C <: Coproduct] {
  type Apply0[In0] = Apply[this.type, State, In0]

  trait SendTo[O] {
    def apply[Cm <: Command, OCm <: Command, Ev <: DomainEvent, Er](command: Cm)(implicit officeInfo: OfficeContract.Aux[O, OCm, Ev, Er], ev: Cm <:< OCm, eventHandler: Apply0[Ev], errorHandler: Apply0[Er], path: OfficePath[O]): Reaction[State]
  }

  def sendTo[O] = new SendTo[O] {
    override def apply[Cm <: Command, OCm <: Command, Ev <: DomainEvent, Er](command: Cm)(implicit officeInfo: OfficeContract.Aux[O, OCm, Ev, Er], ev: Cm <:< OCm, eventHandler: Apply0[Ev], errorHandler: Apply0[Er], path: OfficePath[O]): Reaction[State] = SendCommand(path, command)
  }

  def changeState(newState: State): Reaction[State] = ChangeState(newState)

  def ignore: Reaction[State] = Ignore
}