package pl.newicom.dddd.process.typesafe

import pl.newicom.dddd.aggregate._
import pl.newicom.dddd.office.{AggregateContract, OfficePath}
import pl.newicom.dddd.process.EventDecision

trait Decisions {
  def ignore: EventDecision = EventDecision.Ignore
  def accept: EventDecision = EventDecision.Accept
}

trait Reactions[State] {
  implicit class DeliveryOps[O, Cmd <: Command](officePath: OfficePath[O])(implicit officeContract: AggregateContract[O] { type C = Cmd }) {
    def !!(command: Cmd): EventReaction[State] =
      Deliver(officePath, command)
  }

  def changeState(newState: State): EventReaction[State] = ChangeState(newState)

  def ignore: EventReaction[State] = Ignore
}