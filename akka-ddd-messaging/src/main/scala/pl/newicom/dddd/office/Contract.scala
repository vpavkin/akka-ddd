package pl.newicom.dddd.office

import pl.newicom.dddd.aggregate._

trait Contract[A] {
  type CommandImpl <: Command
  type EventImpl <: DomainEvent
  type ErrorImpl
}

object Contract {
  type Aux[A, Cmd <: Command, Evt <: DomainEvent, Err] = Contract[A] {
    type CommandImpl = Cmd
    type EventImpl = Evt
    type ErrorImpl = Err
  }
}