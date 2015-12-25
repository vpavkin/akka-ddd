package pl.newicom.dddd.messaging

import pl.newicom.dddd.aggregate.EntityId
import pl.newicom.dddd.delivery.protocol.{Processed, Receipt, alod}
import pl.newicom.dddd.messaging.MetaData._

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

  def contains(attrName: String) = content.contains(attrName)

  def get[B](attrName: String) = tryGet[B](attrName).get

  def tryGet[B](attrName: String): Option[B] = content.get(attrName).asInstanceOf[Option[B]]

  def exceptDeliveryAttributes: Option[MetaData] = {
    val resultMap = content.filterKeys(a => !a.startsWith("_"))
    if (resultMap.isEmpty) None else Some(MetaData(resultMap))
  }

  override def toString: String = content.toString()

  def isEmpty: Boolean = content.isEmpty
}

trait MetadataOps { self: Message =>
  def causedBy(msg: Message): MessageImpl = withCausationId(msg.id)

  def addMetadata(metadata: MetaData): MessageImpl = {
    copyWithMetadata(this.metadata.mergeWithMetadata(metadata))
  }


  private def withMetaAttribute(attrName: String, value: Any): MessageImpl = addMetadata(MetaData(Map(attrName.toString -> value)))

  private def getMetaAttribute[B](attrName: String): Option[B] = metadata.tryGet[B](attrName)

  def deliveryReceipt(result: Any = "OK"): Receipt = {
    deliveryId.map(id => alod.Processed(id, result)).getOrElse(Processed(result))
  }

  def withDeliveryId(deliveryId: Long): MessageImpl = withMetaAttribute(DeliveryId, deliveryId)

  def withCausationId(causationId: EntityId): MessageImpl = withMetaAttribute(CausationId, causationId)

  def deliveryId: Option[Long] = getMetaAttribute[Long](DeliveryId)
}

trait Message extends MetadataOps with Serializable {
  def id: String
  type MessageImpl <: Message
  def metadata: MetaData
  def copyWithMetadata(m: MetaData): MessageImpl
}