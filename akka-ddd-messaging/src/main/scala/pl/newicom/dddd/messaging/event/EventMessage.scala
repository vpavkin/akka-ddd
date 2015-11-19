package pl.newicom.dddd.messaging.event

import org.joda.time.DateTime
import pl.newicom.dddd.aggregate.DomainEvent
import pl.newicom.dddd.messaging.MetaData.CorrelationId
import pl.newicom.dddd.messaging.{MetaData, EntityMessage, Message}
import pl.newicom.dddd.utils.UUIDSupport._
import shapeless.syntax.typeable._
import shapeless.Typeable

object EventMessage {
  def unapply[E <: DomainEvent](em: EventMessage[E]): Option[(String, E)] = {
    Some(em.id, em.event)
  }

  def apply[E <: DomainEvent](event0: E, id0: String = uuid, timestamp0: DateTime = new DateTime, metaData0: Option[MetaData] = None): EventMessage[E] = new EventMessage[E] {

    override def event: E = event0

    override def timestamp: DateTime = timestamp0

    override def id: String = id0

    override type MessageImpl = EventMessage[E]

    override def metadata: Option[MetaData] = metaData0

    def copyWithMetadata(newMetaData: Option[MetaData]): MessageImpl = EventMessage(event, id, timestamp, newMetaData)
  }

  implicit def typeable[T <: DomainEvent](implicit castT: Typeable[T]): Typeable[EventMessage[T]] =
    new Typeable[EventMessage[T]]{
      def cast(t: Any): Option[EventMessage[T]] = {
        if(t == null) None
        else if(t.isInstanceOf[EventMessage[_ <: DomainEvent]]) {
          val o = t.asInstanceOf[EventMessage[_ <: DomainEvent]]
          o.event.cast[T].map(_ => t.asInstanceOf[EventMessage[T]])
        } else None
      }
      def describe = s"Option[${castT.describe}]"
    }
}

trait EventMessage[+E <: DomainEvent] extends Message with EntityMessage {

  type MessageImpl <: EventMessage[E]

  def event: E
  def id: String
  def timestamp: DateTime

  override def entityId = tryGetMetaAttribute[String](CorrelationId).orNull
  override def payload = event

  override def toString: String = {
    val msgClass = getClass.getSimpleName
    s"$msgClass(event = $event, id = $id, timestamp = $timestamp, metaData = $metadata)"
  }
}