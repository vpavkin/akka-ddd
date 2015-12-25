package pl.newicom.dddd.scheduling

import java.util.Date

import akka.actor.ActorPath
import pl.newicom.dddd.aggregate.Command
import pl.newicom.dddd.messaging.MetaData
import pl.newicom.dddd.messaging.command.CommandMessage
import pl.newicom.dddd.messaging.event.EventMessage
import pl.newicom.dddd.process.{ReceptorBuilder, ReceptorConfig}
import pl.newicom.dddd.utils.UUIDSupport._

object DeadlinesReceptor {
  private case class TargetedCommandMessage(
    target: ActorPath,
    command: Command,
    id: String = uuid,
    timestamp: Date = new Date,
    metadata: MetaData = MetaData.empty
  ) extends CommandMessage {
    override type MessageImpl = TargetedCommandMessage
    override def copyWithMetadata(m: MetaData): MessageImpl = copy(metadata = m)
  }


  def apply(businessUnit: String): ReceptorConfig = ReceptorBuilder()
    .reactToStream(currentDeadlinesStream(businessUnit))
    .applyTransduction {
      case EventMessage(_, CommandScheduled(_, metadata, command)) =>
        TargetedCommandMessage(metadata.target, command)
    }
    .route {
      case cm: TargetedCommandMessage => cm.target
    }
}
