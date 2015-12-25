package pl.newicom.dddd.process.typesafe

import pl.newicom.dddd.aggregate._
import pl.newicom.dddd.office.{OfficePath, OfficeContract}

sealed trait Reaction[+S] {
  def and[S1 >: S](reaction: Reaction[S1]): Reaction[S1] = And(this, reaction)
}
case class ChangeState[+S](state: S) extends Reaction[S]
case class SendCommand[O, Cmd <: Command, OCmd <: Command, Evt <: DomainEvent, Err](officePath: OfficePath[O], command: Cmd)(implicit contract: OfficeContract.Aux[O, OCmd, Evt, Err], ev: Cmd <:< OCmd) extends Reaction[Nothing]
case class And[+S](first: Reaction[S], second: Reaction[S]) extends Reaction[S]
case object Ignore extends Reaction[Nothing]
