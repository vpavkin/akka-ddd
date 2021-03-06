package pl.newicom.dddd.scheduling

import akka.persistence.Recovery
import pl.newicom.dddd.actor.PassivationConfig
import pl.newicom.dddd.aggregate._
import pl.newicom.dddd.cluster.ShardIdResolver
import pl.newicom.dddd.messaging.correlation.EntityIdResolver
import pl.newicom.dddd.office.AggregateContract.Aux
import pl.newicom.dddd.office._

import scala.reflect.ClassTag

trait SchedulingOffice

object SchedulingOffice {
  implicit val schedulingOffice: OfficeInfo[SchedulingOffice] = new OfficeInfo[SchedulingOffice] {
    def name: String = "deadlines"
  }

  implicit val contract: AggregateContract.Aux[SchedulingOffice, ScheduleCommand, CommandScheduled, Unit] = new AggregateContract[SchedulingOffice] {
    override type C = ScheduleCommand
    override type R = Unit
    override type E = CommandScheduled
  }

  def open()(implicit sr: ShardIdResolver[SchedulingOffice], of: OfficeFactory[SchedulingOffice]): OfficePath[SchedulingOffice] = Office.openOffice[SchedulingOffice](SchedulerBehavior, new AggregateRootActorFactory {
    override def create[O, S, Cm <: Command, Ev <: DomainEvent, Er](pc: PassivationConfig, behavior: AggregateRootBehavior[S, Cm, Ev, Er])(implicit officeInfo: OfficeInfo[O], contract: Aux[O, Cm, Ev, Er], ev: ClassTag[Ev], cm: ClassTag[Cm]): AggregateRootActor[O, S, Cm, Ev, Er] =
      new AggregateRootActor[O, S, Cm, Ev, Er](pc, behavior) {
        override def recovery = Recovery(toSequenceNr = 0L)
        override def preRestart(reason: Throwable, message: Option[Any]) = ()
      }
  })
}
