package pl.newicom.dddd.scheduling

import akka.persistence.Recovery
import pl.newicom.dddd.actor.PassivationConfig
import pl.newicom.dddd.aggregate._
import pl.newicom.dddd.eventhandling.EventPublisher


class SchedulerActor(pc: PassivationConfig) extends AggregateRootActor[SchedulingOffice, Unit, ScheduleCommand, CommandScheduled, Nothing](pc) {
  this: EventPublisher[CommandScheduled] =>

  // Skip recovery
  override def recovery = Recovery(toSequenceNr = 0L)

  // Disable automated recovery on restart
  override def preRestart(reason: Throwable, message: Option[Any]) = ()
}
