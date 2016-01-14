package pl.newicom.dddd.process

import org.joda.time.DateTime
import pl.newicom.dddd.aggregate.Command
import pl.newicom.dddd.office.{AggregateContract, OfficePath}
import pl.newicom.dddd.process.typesafe.{EventReaction, Reactions}
import pl.newicom.dddd.scheduling._


trait ScheduleSupport  {
  def schedulerOffice: OfficePath[SchedulingOffice]

  trait Scheduling[S]  { this: Reactions[S] =>
    implicit class ScheduleOps[O, Cmd <: Command](officePath: OfficePath[O])(implicit officeContract: AggregateContract[O] { type C = Cmd }) {
      trait ScheduleBuilder {
        def at(deadline: DateTime): EventReaction[S]
      }

      def schedule(command: Cmd) = new ScheduleBuilder {
        override def at(deadline: DateTime): EventReaction[S] = {
          schedulerOffice !! ScheduleCommand("global", officePath.value, deadline, command)
        }
      }
    }
  }
}

