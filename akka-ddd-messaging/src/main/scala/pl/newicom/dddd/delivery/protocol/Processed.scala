package pl.newicom.dddd.delivery.protocol

import pl.newicom.dddd.messaging.IdentifiedMessage
import pl.newicom.dddd.utils.UUIDSupport

sealed trait Ack extends IdentifiedMessage {
  def result: Any
}
object Ack {
  def processed(result: Any): Ack = Processed(UUIDSupport.uuid, result)
  def delivered(deliveryId: Long, result: Any): Ack = Delivered(UUIDSupport.uuid, deliveryId, result)
}
case class Processed(id: String, result: Any) extends Ack
case class Delivered(id: String, deliveryId: Long, result: Any) extends Ack
