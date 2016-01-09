package pl.newicom.dddd.messaging

import pl.newicom.dddd.aggregate.EntityId
import pl.newicom.dddd.delivery.protocol.{Processed, Receipt, alod}
import pl.newicom.dddd.messaging.MetaData._

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

  override def toString: String = content.toString()

  def isEmpty: Boolean = content.isEmpty

  def withDeliveryId(deliveryId: Long): MetaData = copy(content = content + (DeliveryId -> deliveryId))
  def deliveryId: Option[Long] = get[Long](DeliveryId)
  def withCausationId(causationId: EntityId): MetaData = copy(content = content + (CausationId -> causationId))
  def causationId: Option[String] = get[String](CausationId)
}


trait Message extends Serializable {
  def id: String
  def metadata: MetaData

  def deliveryReceipt(result: Any = "OK"): Receipt = {
    deliveryId.map(id => alod.Processed(id, result)).getOrElse(Processed(result))
  }

  def deliveryId: Option[Long]
  def withDeliveryId(deliveryId: Long): Message
}
