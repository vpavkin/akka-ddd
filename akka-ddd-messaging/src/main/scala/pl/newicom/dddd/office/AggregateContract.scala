package pl.newicom.dddd.office

import pl.newicom.dddd.aggregate._

trait AggregateContract[A] {
  type CommandImpl <: Command
  type EventImpl <: DomainEvent
  type ErrorImpl
}

object AggregateContract {
  type Aux[A, Cmd <: Command, Evt <: DomainEvent, Err] = AggregateContract[A] {
    type CommandImpl = Cmd
    type EventImpl = Evt
    type ErrorImpl = Err
  }
}