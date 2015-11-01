package pl.newicom.dddd.office

import pl.newicom.dddd.aggregate._

trait Contract[A] {
  type CommandImpl <: Command
  type EventImpl <: DomainEvent
  type ErrorImpl
}
