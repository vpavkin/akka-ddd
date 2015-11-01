package pl.newicom.dddd

import pl.newicom.dddd.messaging.event.ClerkEventStream
import pl.newicom.dddd.office.OfficeInfo

package object scheduling {

  implicit val schedulingOffice: OfficeInfo[SchedulingOffice] = new OfficeInfo[SchedulingOffice] {

    override type CommandImpl = ScheduleEvent
    override type ErrorImpl = Nothing
    override type EventImpl = EventScheduled

    def name: String = "deadlines"
  }

  def currentDeadlinesStream(businessUnit: String) = ClerkEventStream("currentDeadlines", businessUnit)
}
