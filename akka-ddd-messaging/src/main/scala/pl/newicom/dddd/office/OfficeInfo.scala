package pl.newicom.dddd.office

import pl.newicom.dddd.aggregate.{DomainEvent, Command}

trait OfficeInfo[A] extends Contract[A] {
  def name: String
  def isSagaOffice: Boolean = false
}


object OfficeInfo {
  type Aux[A, Cm <: Command, Ev <: DomainEvent, Er] = OfficeInfo[A] {
    type CommandImpl = Cm
    type EventImpl = Ev
    type ErrorImpl = Er
  }
}