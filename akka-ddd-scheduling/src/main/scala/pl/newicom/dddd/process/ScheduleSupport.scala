package pl.newicom.dddd.process

import akka.persistence.AtLeastOnceDelivery
import org.joda.time.DateTime
import pl.newicom.dddd.aggregate.{Command, DomainEvent}
import pl.newicom.dddd.office.{OfficeContract, OfficePath}
import pl.newicom.dddd.scheduling._


trait ScheduleSupport extends AtLeastOnceDeliveryOps { self: AtLeastOnceDelivery =>
  def scheduleOffice: OfficePath[SchedulingOffice]

  implicit class ScheduleOps[O, Cmd <: Command, Evt <: DomainEvent, Err](officePath: OfficePath[O])(implicit officeContract: OfficeContract.Aux[O, Cmd, Evt, Err]) {
    trait ScheduleBuilder {
      def at(deadline: DateTime): Unit
    }

    def schedule[C <: Cmd](command: C) = new ScheduleBuilder {
      override def at(deadline: DateTime): Unit = {
        scheduleOffice !! ScheduleCommand("global", officePath.value, deadline, command)
      }

    }
  }
}
