package pl.newicom.dddd

import pl.newicom.dddd.aggregate.AggregateRoot
import pl.newicom.dddd.messaging.event.ClerkEventStream
import pl.newicom.dddd.office.{OfficeContract, OfficeInfo}

package object scheduling {
  implicit val schedulingOffice: OfficeInfo[SchedulingOffice] = new OfficeInfo[SchedulingOffice] {
    def name: String = "deadlines"
  }

  implicit val contract: OfficeContract.Aux[SchedulingOffice, ScheduleCommand, CommandScheduled, Nothing] = new OfficeContract[SchedulingOffice] {
    override type CommandImpl = ScheduleCommand
    override type ErrorImpl = Nothing
    override type EventImpl = CommandScheduled
  }

  def currentDeadlinesStream(businessUnit: String) = ClerkEventStream("currentDeadlines", businessUnit)

  implicit val state = new AggregateRoot[SchedulingOffice] {
    type State = Unit
    override type EventImpl = CommandScheduled
    override type CommandImpl = ScheduleCommand
    override type ErrorImpl = Nothing

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
}
