package pl.newicom.dddd.messaging.command

import java.util.Date

import pl.newicom.dddd.aggregate.{Command, EntityId}
import pl.newicom.dddd.messaging.{MetaData, EntityMessage, Message}
import pl.newicom.dddd.utils.UUIDSupport.uuid

trait CommandMessage extends Message with EntityMessage {
  def command: Command
  type MessageImpl <: CommandMessage
  override def entityId: EntityId = command.aggregateId
  override def payload: Any = command
}

private [command] case class CommandMessageImpl(
    command: Command,
    id: String = uuid,
    timestamp: Date = new Date,
    metadata: MetaData = MetaData.empty)
  extends CommandMessage {

  type MessageImpl = CommandMessageImpl

  override def copyWithMetadata(m: MetaData): CommandMessageImpl = copy(metadata = m)
}

object CommandMessage {
  def apply(command: Command): CommandMessage = CommandMessageImpl(command)
}