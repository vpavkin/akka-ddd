package pl.newicom.dddd.messaging.event

import org.joda.time.DateTime
import pl.newicom.dddd.aggregate.DomainEvent
import pl.newicom.dddd.messaging.{EntityMessage, Message, MetaData}
import pl.newicom.dddd.utils.UUIDSupport._
import shapeless.Typeable
import shapeless.syntax.typeable._

import scala.reflect.ClassTag

object EventMessage {
  def unapply[E <: DomainEvent](em: EventMessage[E]): Option[(String, E)] = {
    Some(em.id, em.event)
  }

  def apply[E <: DomainEvent](event: E, id: String = uuid, timestamp: DateTime = new DateTime, metaData: MetaData = MetaData.empty) = DefaultEventMessage(event, id, timestamp, metaData)

  implicit def typeable[E <: DomainEvent : Typeable]: Typeable[EventMessage[E]] =
    new Typeable[EventMessage[E]]{
      def cast(t: Any): Option[EventMessage[E]] =
        Option(t).collect {
          case em: EventMessage[_] => em
        }
        .flatMap(_.event.cast[E])
        .map(_ => t.asInstanceOf[EventMessage[E]])
      def describe = s"EventMessage[${Typeable[E].describe}]"
    }
}

sealed trait EventMessage[+E <: DomainEvent] extends Message with EntityMessage {
  def event: E
  def timestamp: DateTime
  override def entityId = event.aggregateId
  override def payload = event
}

case class DefaultEventMessage[+E <: DomainEvent](event: E, id: String, timestamp: DateTime, metadata: MetaData) extends EventMessage[E] {
  def addMetadata(other: MetaData): EventMessage[E] = copy(metadata = metadata.mergeWithMetadata(other))
  def causedBy(msg: Message): EventMessage[E] = copy(metadata = metadata.withCausationId(msg.id))

  override def copyWithMetadata(metaData: MetaData): Message = copy(metadata = metaData)
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

  override def copyWithMetadata(metaData: MetaData): Message = copy(metadata = metaData)
}