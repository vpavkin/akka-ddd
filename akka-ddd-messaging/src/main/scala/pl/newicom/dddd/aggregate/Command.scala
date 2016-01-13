package pl.newicom.dddd.aggregate

import shapeless.Typeable

import scala.reflect.ClassTag

trait Command {
  def aggregateId: String
}

object Command {
  implicit def typeable[C <: Command](implicit C: ClassTag[C]): Typeable[C] = new Typeable[C] {
    override def describe: String = C.toString()
    override def cast(t: Any): Option[C] = Option(t).collect {
      case c: C => c
    }
  }
}
