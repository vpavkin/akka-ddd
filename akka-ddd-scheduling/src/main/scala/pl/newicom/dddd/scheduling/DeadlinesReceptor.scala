package pl.newicom.dddd.scheduling

import pl.newicom.dddd.messaging.command.CommandMessage
import pl.newicom.dddd.messaging.event.EventMessage
import pl.newicom.dddd.process.ReceptorConfig

object DeadlinesReceptor {
  def apply(businessUnit: String): ReceptorConfig =
    ReceptorConfig.reactToStream(currentDeadlinesStream(businessUnit)).extractReceiver {
      case EventMessage(_, CommandScheduled(_, metadata, command)) =>
        metadata.target -> CommandMessage(command)
    }

}
