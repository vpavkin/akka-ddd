package pl.newicom.dddd.messaging.event

import org.joda.time.DateTime
import pl.newicom.dddd.aggregate.DomainEvent
import pl.newicom.dddd.messaging.MetaData
import pl.newicom.dddd.utils.UUIDSupport._


object DomainEventMessage {
  def apply[E <: DomainEvent](em: EventMessage[E], snapshotId: AggregateSnapshotId): DomainEventMessage[E] = DomainEventMessage(snapshotId, em.event, em.id, em.timestamp, None)
}

case class DomainEventMessage[+E <: DomainEvent](
    snapshotId: AggregateSnapshotId,
    event: E,
    id: String = uuid,
    timestamp: DateTime = new DateTime,
    metadata: Option[MetaData] = None)
  extends EventMessage[E] {

  override type MessageImpl <: DomainEventMessage[E]

  override def entityId = aggregateId



  override def copyWithMetadata(m: Option[MetaData]): MessageImpl = copy(metadata = m).asInstanceOf[MessageImpl]

  def aggregateId = snapshotId.aggregateId

  def sequenceNr = snapshotId.sequenceNr

}