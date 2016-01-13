package pl.newicom.dddd

import shapeless.Typeable

import scala.reflect.ClassTag

package object aggregate {

  type EntityId = String

  trait DomainEvent {
    def aggregateId: EntityId
  }

  object DomainEvent {
    implicit def typeable[E <: DomainEvent](implicit E: ClassTag[E]): Typeable[E] = new Typeable[E] {
      override def describe: String = E.toString()

      override def cast(t: Any): Option[E] = Option(t).collect {
        case e: E => e
      }
    }
  }
}
