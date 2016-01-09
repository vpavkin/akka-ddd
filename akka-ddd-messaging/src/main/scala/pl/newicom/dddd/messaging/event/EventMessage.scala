package pl.newicom.dddd.messaging.event

import org.joda.time.DateTime
import pl.newicom.dddd.aggregate.DomainEvent
import pl.newicom.dddd.messaging.{EntityMessage, Message, MetaData}
import pl.newicom.dddd.utils.UUIDSupport._
import shapeless.Typeable
import shapeless.syntax.typeable._

object EventMessage {
  def unapply[E <: DomainEvent](em: EventMessage[E]): Option[(String, E)] = {
    Some(em.id, em.event)
  }

  def apply[E <: DomainEvent](event: E, id: String = uuid, timestamp: DateTime = new DateTime, metaData: MetaData = MetaData.empty) = DefaultEventMessage(event, id, timestamp, metaData)

  implicit def typeable[T <: DomainEvent](implicit castT: Typeable[T]): Typeable[EventMessage[T]] =
    new Typeable[EventMessage[T]]{
      def cast(t: Any): Option[EventMessage[T]] = {
        if(t == null) None
        else if(t.isInstanceOf[EventMessage[_ <: DomainEvent]]) {
          val o = t.asInstanceOf[EventMessage[_ <: DomainEvent]]
          o.event.cast[T].map(_ => t.asInstanceOf[EventMessage[T]])
        } else None
      }
      def describe = s"EventMessage[${castT.describe}]"
    }
}

case class DefaultEventMessage[+E <: DomainEvent](event: E, id: String, timestamp: DateTime, metadata: MetaData) extends EventMessage[E] {
//  override type MessageImpl = this.type

  override def withDeliveryId(deliveryId: Long): Message = copy(metadata = metadata.withDeliveryId(deliveryId))

  override def deliveryId: Option[Long] = metadata.deliveryId

  def addMetadata(other: MetaData): EventMessage[E] = copy(metadata = metadata.mergeWithMetadata(other))
  def causedBy(msg: Message): EventMessage[E] = copy(metadata = metadata.withCausationId(msg.id))
}

sealed trait EventMessage[+E <: DomainEvent] extends Message with EntityMessage {
  def event: E
  def id: String
  def timestamp: DateTime
  override def entityId = event.aggregateId
  override def payload = event
}

object DomainEventMessage {
  def apply[E <: DomainEvent](em: EventMessage[E], snapshotId: AggregateSnapshotId): DomainEventMessage[E] =
    DomainEventMessage(snapshotId, em.event, em.id, em.timestamp, em.metadata)
}

case class DomainEventMessage[+E <: DomainEvent](
                                                  snapshotId: AggregateSnapshotId,
                                                  event: E,
                                                  id: String = uuid,
                                                  timestamp: DateTime = new DateTime,
                                                  metadata: MetaData = MetaData.empty)
  extends EventMessage[E] {

  override def entityId = aggregateId


  def aggregateId = snapshotId.aggregateId

  def sequenceNr = snapshotId.sequenceNr

  override def deliveryId: Option[Long] = metadata.deliveryId

  override def withDeliveryId(deliveryId: Long): Message = copy(metadata = metadata.withDeliveryId(deliveryId))
  def addMetadata(other: MetaData): DomainEventMessage[E] = copy(metadata = metadata.mergeWithMetadata(other))
}