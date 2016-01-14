package pl.newicom.dddd.office

import pl.newicom.dddd.aggregate._

/**
  * Type class representing aggregate office messaging contract
  * @tparam O - Office this contract relates to
  */
trait AggregateContract[O] {
  /**
    * Command type accepted by aggregate
    */
  type C <: Command
  /**
    * Event type issued issued by aggreagate as a result of processed command
    */
  type E <: DomainEvent
  /**
    * Rejection type issued by aggregate as a response to rejected command
    */
  type R
}

object AggregateContract {
  type Aux[A, C0 <: Command, E0 <: DomainEvent, R0] = AggregateContract[A] {
    type C = C0
    type E = E0
    type R = R0
  }
  type AuxCR[A, C0 <: Command, R0] = AggregateContract[A] {
    type C = C0
    type R = R0
  }
}