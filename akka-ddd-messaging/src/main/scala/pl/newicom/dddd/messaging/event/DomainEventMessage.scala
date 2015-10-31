package pl.newicom.dddd.messaging.event

import org.joda.time.DateTime
import pl.newicom.dddd.aggregate.DomainEvent
import pl.newicom.dddd.utils.UUIDSupport._

case class DomainEventMessage[E <: DomainEvent](
    snapshotId: AggregateSnapshotId,
    override val event: E,
    override val id: String = uuid,
    override val timestamp: DateTime = new DateTime)
  extends EventMessage(event, id, timestamp) {

  override type MessageImpl = DomainEventMessage[E]

  override def entityId = aggregateId

  def this(em: EventMessage[E], s: AggregateSnapshotId) = this(s, em.event, em.id, em.timestamp)

  def aggregateId = snapshotId.aggregateId

  def sequenceNr = snapshotId.sequenceNr

}