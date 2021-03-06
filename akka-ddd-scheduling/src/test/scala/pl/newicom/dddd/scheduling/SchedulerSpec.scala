package pl.newicom.dddd.scheduling

import akka.actor.Props
import org.joda.time.DateTime
import pl.newicom.dddd.actor.PassivationConfig
import pl.newicom.dddd.aggregate.AggregateRootActorFactory
import pl.newicom.dddd.eventhandling.LocalEventPublisher
import pl.newicom.dddd.test.support.OfficeSpec
import pl.newicom.dddd.test.support.TestConfig.testSystem
import SchedulerSpec._
import scala.concurrent.duration._

object SchedulerSpec {
  val businessUnit = "test"

  implicit def actorFactory(implicit it: Duration = 1.minute): AggregateRootActorFactory[SchedulingOffice] =
    new AggregateRootActorFactory[SchedulingOffice] {
      override def props(pc: PassivationConfig): Props = Props(new SchedulerActor(pc) with LocalEventPublisher[CommandScheduled])
      override def inactivityTimeout: Duration = it
    }

}

class SchedulerSpec extends OfficeSpec[SchedulingOffice](Some(testSystem)) {

  "Scheduling office" should {
    "schedule event" in {
      when {
        ScheduleCommand(businessUnit, null, DateTime.now().plusMinutes(1), null)
      }
        .expect { c =>
          CommandScheduled(
            ScheduledCommandMetadata(
              c.businessUnit,
              c.target,
              c.deadline.withSecondOfMinute(0).withMillisOfSecond(0),
              c.deadline.getMillis),
            c.command)
        }
    }
  }

}
