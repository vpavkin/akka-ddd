package pl.newicom.dddd.messaging

import pl.newicom.dddd.aggregate.EntityId
import pl.newicom.dddd.delivery.protocol.Ack
import pl.newicom.dddd.messaging.MetaData._
import pl.newicom.dddd.utils.UUIDSupport.uuid

import scala.reflect.ClassTag

object MetaData {
  private [messaging] val DeliveryId          = "_deliveryId"
  private [messaging] val CausationId         = "causationId"

  def empty: MetaData = MetaData(Map.empty)
}

case class MetaData(content: Map[String, Any]) extends Serializable {
  def mergeWithMetadata(metadata: MetaData): MetaData = {
    addContent(metadata.content)
  }

  def addContent(content: Map[String, Any]): MetaData = {
    copy(content = this.content ++ content)
  }

  def get[B : ClassTag](attrName: String): Option[B] = content.get(attrName).collect { case b: B => b }
  def isEmpty: Boolean = content.isEmpty
  def withDeliveryId(deliveryId: Long): MetaData = copy(content = content + (DeliveryId -> deliveryId))
  def deliveryId: Option[Long] = get[Long](DeliveryId)
  def withCausationId(causationId: EntityId): MetaData = copy(content = content + (CausationId -> causationId))
  def causationId: Option[String] = get[String](CausationId)

  override def toString: String = content.toString()
}

trait IdentifiedMessage {
  def id: String
}

trait Message extends IdentifiedMessage with Serializable {
  def metadata: MetaData

  def ack(result: Any): Ack =
    metadata.deliveryId.map(deliveryId => Ack.delivered(deliveryId, result)).getOrElse(Ack.processed(result))

  def withDeliveryId(deliveryId: Long): Message = copyWithMetadata(metadata.withDeliveryId(deliveryId))

  def causedBy(message: IdentifiedMessage): Message = copyWithMetadata(metadata.withCausationId(message.id))

  def copyWithMetadata(metaData: MetaData): Message
}
