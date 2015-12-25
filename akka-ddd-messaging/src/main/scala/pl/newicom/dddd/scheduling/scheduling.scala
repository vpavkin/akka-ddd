package pl.newicom.dddd.scheduling

import akka.actor.ActorPath
import org.joda.time.DateTime
import pl.newicom.dddd.aggregate.{EntityId, Command, DomainEvent}

  //
  // Commands
  //
  case class ScheduleCommand(businessUnit: String, target: ActorPath, deadline: DateTime, command: Command) extends Command {
    def aggregateId = businessUnit
  }

  // 
  // Events
  // 
  case class CommandScheduled(businessUnit: String, metadata: ScheduledCommandMetadata, command: Command) extends DomainEvent {
    override def aggregateId: EntityId = businessUnit
  }
 case class ScheduledCommandMetadata(target: ActorPath, deadline: DateTime, deadlineMillis: Long)