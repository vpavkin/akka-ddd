package pl.newicom.dddd.scheduling

import akka.actor.ActorPath
import org.joda.time.DateTime
import pl.newicom.dddd.aggregate.{Command, DomainEvent}

  //
  // Commands
  //
  case class ScheduleCommand(businessUnit: String, target: ActorPath, deadline: DateTime, command: Command) extends Command {
    def aggregateId = businessUnit
  }

  // 
  // Events
  // 
  case class CommandScheduled(metadata: ScheduledCommandMetadata, event: DomainEvent)
 case class ScheduledCommandMetadata(businessUnit: String, target: ActorPath, deadline: DateTime, deadlineMillis: Long)