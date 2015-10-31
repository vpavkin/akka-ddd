package pl.newicom.dddd.messaging.event

import org.joda.time.DateTime
import pl.newicom.dddd.aggregate.DomainEvent
import pl.newicom.dddd.messaging.MetaData.CorrelationId
import pl.newicom.dddd.messaging.{EntityMessage, Message}
import pl.newicom.dddd.utils.UUIDSupport._

object EventMessage {
  def unapply[E <: DomainEvent](em: EventMessage[E]): Option[(String, E)] = {
    Some(em.id, em.event)
  }
}

class EventMessage[+E <: DomainEvent](
    val event: E,
    val id: String = uuid,
    val timestamp: DateTime = new DateTime)
  extends Message with EntityMessage {

  type MessageImpl <: EventMessage[E]

  override def entityId = tryGetMetaAttribute[String](CorrelationId).orNull
  override def payload = event

  override def toString: String = {
    val msgClass = getClass.getSimpleName
    s"$msgClass(event = $event, id = $id, timestamp = $timestamp, metaData = $metadata)"
  }
}