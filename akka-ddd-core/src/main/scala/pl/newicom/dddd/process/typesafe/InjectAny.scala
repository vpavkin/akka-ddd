package pl.newicom.dddd.process.typesafe

import shapeless._
import shapeless.syntax.typeable._

trait InjectAny[C <: Coproduct] extends Serializable {
  def apply(a: Any): Option[C]
}

object InjectAny {
  implicit def ccons[H : Typeable, T <: Coproduct](implicit tcp: Lazy[InjectAny[T]]): InjectAny[H :+: T] = new InjectAny[H :+: T] {
    def apply(a: Any): Option[H :+: T] = {
      a.cast[H].map(Coproduct[H :+: T](_)).orElse(tcp.value(a).map(_.extendLeft[H]))
    }
  }
  implicit val cnil: InjectAny[CNil] = new InjectAny[CNil] {
    override def apply(a: Any): Option[CNil] = None
  }
}
