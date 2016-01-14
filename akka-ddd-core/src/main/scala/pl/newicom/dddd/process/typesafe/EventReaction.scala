package pl.newicom.dddd.process.typesafe

import pl.newicom.dddd.aggregate._
import pl.newicom.dddd.office.{OfficePath, AggregateContract}

sealed trait EventReaction[+S] {
  def and[S1 >: S](reaction: EventReaction[S1]): EventReaction[S1] = And(this, reaction)
}
case class ChangeState[+S](state: S) extends EventReaction[S]
case class Deliver[O, +Cmd <: Command](officePath: OfficePath[O], command: Cmd)(implicit officeContract: AggregateContract[O] { type C = Cmd }) extends EventReaction[Nothing]
case class And[+S](first: EventReaction[S], second: EventReaction[S]) extends EventReaction[S]
case object Ignore extends EventReaction[Nothing]
