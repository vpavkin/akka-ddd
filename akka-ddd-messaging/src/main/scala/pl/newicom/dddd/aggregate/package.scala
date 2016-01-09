package pl.newicom.dddd

package object aggregate {

  type EntityId = String

  trait DomainEvent {
    def aggregateId: EntityId
  }

}
