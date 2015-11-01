package pl.newicom.dddd.scheduling

import akka.persistence.Recovery
import pl.newicom.dddd.actor.PassivationConfig
import pl.newicom.dddd.aggregate._
import pl.newicom.dddd.eventhandling.EventPublisher


sealed trait Scheduler

object Scheduler {

  implicit val state = new AggregateRoot[Scheduler, SchedulingOffice] {
    override type EventImpl = EventScheduled
    override type CommandImpl = ScheduleEvent
    override type ErrorImpl = Nothing

    override def processFirstCommand: ProcessFirstCommand = {
      case e: ScheduleEvent => processCommand(new Scheduler {})(e)
    }
    override def applyFirstEvent: ApplyFirstEvent = {case _ => new Scheduler {}}

    override def processCommand(state: Scheduler): ProcessCommand = {
      case ScheduleEvent(bu, target, deadline, msg) =>
        accept(
          EventScheduled(
            bu,
            target,
            deadline.withSecondOfMinute(0).withMillisOfSecond(0),
            deadline.getMillis,
            msg)
        )
    }
    override def applyEvent(state: Scheduler): ApplyEvent = Function.const(state)
  }
}

class SchedulerActor(val pc: PassivationConfig) extends AggregateRootActor[Scheduler, SchedulingOffice, ScheduleEvent, EventScheduled, Nothing] {
  this: EventPublisher[EventScheduled] =>

  // Skip recovery
  override def recovery = Recovery(toSequenceNr = 0L)

  // Disable automated recovery on restart
  override def preRestart(reason: Throwable, message: Option[Any]) = ()
}
