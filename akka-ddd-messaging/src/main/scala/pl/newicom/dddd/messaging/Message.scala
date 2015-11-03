package pl.newicom.dddd.messaging

import pl.newicom.dddd.aggregate.EntityId
import pl.newicom.dddd.delivery.protocol.{Receipt, Processed, alod}
import pl.newicom.dddd.messaging.Metadata._

import scala.util.{Success, Try}

object Metadata {
  val DeliveryId          = "_deliveryId"
  val CausationId         = "causationId"
  val CorrelationId       = "correlationId"
  val SessionId           = "sessionId"

  def empty: Metadata = Metadata(Map.empty)
}

case class Metadata(content: Map[String, Any]) extends Serializable {

  def mergeWithMetadata(metadata: Option[Metadata]): Metadata = {
    metadata.map(_.content).map(addContent).getOrElse(this)
  }

  def addContent(content: Map[String, Any]): Metadata = {
    copy(content = this.content ++ content)
  }

  def contains(attrName: String) = content.contains(attrName)

  def get[B](attrName: String) = tryGet[B](attrName).get

  def tryGet[B](attrName: String): Option[B] = content.get(attrName).asInstanceOf[Option[B]]

  def exceptDeliveryAttributes: Option[Metadata] = {
    val resultMap = content.filterKeys(a => !a.startsWith("_"))
    if (resultMap.isEmpty) None else Some(Metadata(resultMap))
  }

  override def toString: String = content.toString()
}

trait Message extends Serializable {

  def id: String

  type MessageImpl <: Message

  def copyWithMetadata(m: Option[Metadata]): MessageImpl
  def metadata: Option[Metadata]

  def causedBy(msg: Message): MessageImpl =
    addMetadata(msg.metadataExceptDeliveryAttributes)
      .withCausationId(msg.id).asInstanceOf[MessageImpl]

  def metadataExceptDeliveryAttributes: Option[Metadata] = {
    metadata.flatMap(_.exceptDeliveryAttributes)
  }

  def addMetadata(metadata: Option[Metadata]): MessageImpl = {
    copyWithMetadata(this.metadata.map(_.mergeWithMetadata(metadata)).orElse(metadata))
  }

  def addMetadataContent(metadataContent: Map[String, Any]): MessageImpl = {
    addMetadata(Some(Metadata(metadataContent)))
  }

  def withMetaAttribute(attrName: String, value: Any): MessageImpl = addMetadataContent(Map(attrName.toString -> value))

  def hasMetaAttribute(attrName: String) = metadata.exists(_.contains(attrName))

  def getMetaAttribute[B](attrName: String): B = tryGetMetaAttribute[B](attrName).get

  def tryGetMetaAttribute[B](attrName: String): Option[B] = if (metadata.isDefined) metadata.get.tryGet[B](attrName.toString) else None

  def deliveryReceipt(result: Try[Any] = Success("OK")): Receipt = {
    deliveryId.map(id => alod.Processed(id, result)).getOrElse(Processed(result))
  }

  def withDeliveryId(deliveryId: Long) = withMetaAttribute(DeliveryId, deliveryId)

  def withCorrelationId(correlationId: EntityId) = withMetaAttribute(CorrelationId, correlationId)

  def withCausationId(causationId: EntityId) = withMetaAttribute(CausationId, causationId)

  def withSessionId(sessionId: EntityId) = withMetaAttribute(SessionId, sessionId)

  def deliveryId: Option[Long] = tryGetMetaAttribute[Any](DeliveryId).map {
    case bigInt: scala.math.BigInt => bigInt.toLong
    case l: Long => l
  }

  def correlationId: Option[EntityId] = tryGetMetaAttribute[EntityId](CorrelationId)

}