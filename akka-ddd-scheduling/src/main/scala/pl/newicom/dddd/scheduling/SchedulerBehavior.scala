package pl.newicom.dddd.scheduling

import pl.newicom.dddd.aggregate.AggregateRootBehavior

object SchedulerBehavior extends AggregateRootBehavior[Unit, ScheduleCommand, CommandScheduled, Unit] {
  override def processFirstCommand: ProcessFirstCommand = {
    case e: ScheduleCommand => processCommand(())(e)
  }
  override def applyFirstEvent: ApplyFirstEvent = {case _ => ()}

  override def processCommand(state: Unit): ProcessCommand = {
    case ScheduleCommand(bu, target, deadline, msg) =>
      val metadata = ScheduledCommandMetadata(
        target,
        deadline.withSecondOfMinute(0).withMillisOfSecond(0),
        deadline.getMillis
      )
      accept(
        CommandScheduled(bu, metadata, msg)
      )
  }
  override def applyEvent(state: Unit): ApplyEvent = Function.const(state)
}
