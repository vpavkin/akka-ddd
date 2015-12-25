package pl.newicom.dddd.office

import pl.newicom.dddd.aggregate._

trait OfficeContract[A] {
  type CommandImpl <: Command
  type EventImpl <: DomainEvent
  type ErrorImpl
}

object OfficeContract {
  type Aux[A, Cmd <: Command, Evt <: DomainEvent, Err] = OfficeContract[A] {
    type CommandImpl = Cmd
    type EventImpl = Evt
    type ErrorImpl = Err
  }
}