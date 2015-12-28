package pl.newicom.dddd.messaging.command

import java.util.Date

import pl.newicom.dddd.aggregate.{Command, EntityId}
import pl.newicom.dddd.messaging.{MetaData, EntityMessage, Message}
import pl.newicom.dddd.utils.UUIDSupport.uuid

trait CommandMessage extends Message with EntityMessage {
  def command: Command
  override def entityId: EntityId = command.aggregateId
  override def payload: Any = command
  def causedBy(msg: Message): CommandMessage
}

private [command] case class CommandMessageImpl(
    command: Command,
    id: String = uuid,
    timestamp: Date = new Date,
    metadata: MetaData = MetaData.empty)
  extends CommandMessage {

  override def deliveryId: Option[Long] = metadata.deliveryId

  override def withDeliveryId(deliveryId: Long): Message = copy(metadata = metadata.withDeliveryId(deliveryId))

  override def causedBy(msg: Message): CommandMessage = copy(metadata = metadata.withCausationId(msg.id))
}

object CommandMessage {
  def apply(command: Command): CommandMessage = CommandMessageImpl(command)
}