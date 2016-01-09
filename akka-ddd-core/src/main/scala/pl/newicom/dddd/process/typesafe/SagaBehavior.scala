package pl.newicom.dddd.process.typesafe

import pl.newicom.dddd.aggregate._
import pl.newicom.dddd.office.{OfficeContract, OfficePath}
import pl.newicom.dddd.process.EventDecision

trait Decisions {
  def ignore: EventDecision = EventDecision.Ignore
  def accept: EventDecision = EventDecision.Accept
}

trait Reactions[State] {
  implicit class DeliveryOps[O, Cmd <: Command](officePath: OfficePath[O])(implicit officeContract: OfficeContract[O] { type CommandImpl = Cmd }) {
    def !!(command: Cmd): EventReaction[State] =
      Deliver(officePath, command)
  }

  def changeState(newState: State): EventReaction[State] = ChangeState(newState)

  def ignore: EventReaction[State] = Ignore
}