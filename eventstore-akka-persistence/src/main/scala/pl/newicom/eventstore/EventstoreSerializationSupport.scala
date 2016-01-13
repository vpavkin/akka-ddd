package pl.newicom.eventstore

import akka.actor.ActorSystem
import akka.persistence.PersistentRepr
import akka.persistence.eventstore.snapshot.EventStoreSnapshotStore.SnapshotEvent
import akka.util.ByteString
import eventstore.Content._
import eventstore.{Content, ContentType, EventData}
import org.joda.time.DateTime
import pl.newicom.dddd.aggregate._
import pl.newicom.dddd.messaging.MetaData
import pl.newicom.dddd.messaging.event.{AggregateSnapshotId, DomainEventMessage, EventMessage}
import pl.newicom.dddd.serialization.JsonSerHints._
import pl.newicom.eventstore.json.JsonSerializerExtension

import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

/**
 * Contains methods for converting akka.persistence.PersistentRepr from/to eventstore.EventData.
 * Payload of deserialized (in-memory) PersistentRepr is EventMessage.
 * During serialization of PersistentRepr, its payload is replaced with payload of EventMessage (actual event) while
 * metadata of EventMessage is stored in metadata of eventstore.EventData.
 * EventType of eventstore.EventData is set to class of actual event.
 * </br>
 * During deserialization original (in-memory) PersistentRepr is reconstructed.
 */
trait EventstoreSerializationSupport {

  lazy val jsonSerializer = JsonSerializerExtension(system)
  lazy val serializationHints = fromConfig(system.settings.config)

  def system: ActorSystem

  def toEventData(x: AnyRef, contentType: ContentType): EventData = {
    def toContent(o: AnyRef, eventType: Option[String] = None) =
      Content(ByteString(serialize(o, eventType)), contentType)

    x match {
      case x: PersistentRepr =>
        x.payload match {
          case em @ EventMessage(_, _) =>
            val (event, md) = toPayloadAndMetadata(em)
            val eventType = classFor(event).getName
            EventData(
              eventType = eventType,
              data = toContent(x.withPayload(event), Some(eventType)),
              metadata = if (md.isEmpty) Empty else toContent(md)
            )
          case _ =>
            EventData(eventType = classFor(x).getName, data = toContent(x))
        }

      case x: SnapshotEvent =>
        EventData(eventType = classFor(x).getName, data = toContent(x))

      case _ => sys.error(s"Cannot serialize $x")
    }
  }

  def toDomainEventMessage[E <: DomainEvent](eventData: EventData): DomainEventMessage[E] = {
    val pr = toPersistentRepr(eventData)
    val em = pr.payload.asInstanceOf[EventMessage[E]]
    val snapshotId = new AggregateSnapshotId(pr.persistenceId, pr.sequenceNr)
    DomainEventMessage(em, snapshotId)
  }

  def toEventMessage(eventData: EventData): EventMessage[DomainEvent] = {
    val pr = toPersistentRepr(eventData)
    pr.payload.asInstanceOf[EventMessage[DomainEvent]]
  }

  def toPersistentRepr(event: EventData): PersistentRepr = {
    val bytes = event.data.value.toArray[Byte]
    val eventType = Some(event.eventType)
    val repr = deserialize[PersistentRepr](bytes, eventType)
    Some(event.metadata).collect {
      case m if m.value.nonEmpty => deserialize[MetaData](m.value.toArray)
    }.map { metadata =>
      repr.withPayload(fromPayloadAndMetadata(repr.payload.asInstanceOf[DomainEvent], metadata))
    }.getOrElse(repr)
  }

  private def toPayloadAndMetadata(em: EventMessage[DomainEvent]): (DomainEvent, MetaData) =
    (em.event, em.metadata.addContent(Map("id" -> em.id, "timestamp" -> em.timestamp)))

  private def fromPayloadAndMetadata(payload: DomainEvent, metadata: MetaData): EventMessage[DomainEvent] = {
    val id: EntityId = metadata.get[EntityId]("id").get
    val timestamp = metadata.get[String]("timestamp").map(DateTime.parse).get
    EventMessage(payload, id, timestamp).addMetadata(metadata)
  }

  private def deserialize[A: ClassTag](bytes: Array[Byte], eventType: Option[String] = None): A = {
    jsonSerializer.fromBinary[A](bytes, serializationHints ++ eventType.toList)
  }

  private def serialize(o : AnyRef, eventType: Option[String] = None): Array[Byte] =
    jsonSerializer.toBinary(o, serializationHints ++ eventType.toList)

  private def classFor(x: AnyRef) = x match {
    case x: PersistentRepr => classOf[PersistentRepr]
    case _                 => x.getClass
  }

}
