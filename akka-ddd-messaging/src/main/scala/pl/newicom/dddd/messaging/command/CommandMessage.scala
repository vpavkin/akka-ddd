package pl.newicom.dddd.messaging.command

import java.util.Date

import pl.newicom.dddd.aggregate.{Command, EntityId}
import pl.newicom.dddd.messaging._
import pl.newicom.dddd.utils.UUIDSupport.uuid
import shapeless.Typeable
import shapeless.syntax.typeable._

object CommandMessage {
  implicit def typeable[C <: Command](implicit castT: Typeable[C]): Typeable[CommandMessage[C]] =
    new Typeable[CommandMessage[C]]{
      def cast(t: Any): Option[CommandMessage[C]] = {
        Option(t).collect {
          case cm: CommandMessage[_] => cm
        }.flatMap { cm =>
          cm.command.cast[C].map(_ => t.asInstanceOf[CommandMessage[C]])
        }
      }
      def describe = s"CommandMessage[${castT.describe}]"
    }

  def apply[C <: Command](command: C): CommandMessage[C] = CommandMessage(command, id = uuid, timestamp = new Date, metadata = MetaData.empty)
}

case class CommandMessage[+C <: Command](command: C, id: String, timestamp: Date, metadata: MetaData) extends Message with EntityMessage {
  override def entityId: EntityId = command.aggregateId
  override def payload: Any = command
  override def copyWithMetadata(metaData: MetaData): Message = copy(metadata = metaData)
}