package pl.newicom.dddd.office

import akka.actor.{ActorRef, Props}
import pl.newicom.dddd.actor.BusinessEntityActorFactory
import pl.newicom.dddd.aggregate.{AggregateRootActorFactory, AggregateRootBehavior, Command, DomainEvent}
import pl.newicom.dddd.cluster.ShardIdResolver
import pl.newicom.dddd.messaging.correlation.EntityIdResolver
import pl.newicom.dddd.office.OfficeContract.Aux

import scala.reflect.ClassTag

object Office {
  def office[O : OfficeInfo : BusinessEntityActorFactory : EntityIdResolver : ShardIdResolver : OfficeFactory]: ActorRef = {
    OfficeFactory[O].getOrCreate(implicitly[OfficeInfo[O]].name, implicitly[BusinessEntityActorFactory[O]], implicitly[EntityIdResolver[O]])
  }

  abstract class OpenOffice[O] {
    def apply[S, Cmd <: Command : ClassTag, Evt <: DomainEvent : ClassTag, Err](behavior: AggregateRootBehavior[S, Cmd, Evt, Err], factory: AggregateRootActorFactory = AggregateRootActorFactory.default)(implicit contract: OfficeContract.Aux[O, Cmd, Evt, Err], officeInfo: OfficeInfo[O], eir: EntityIdResolver[O], sr: ShardIdResolver[O], of: OfficeFactory[O]): OfficePath[O]
  }

  def openOffice[O] = new OpenOffice[O] {
    override def apply[S, Cmd <: Command : ClassTag, Evt <: DomainEvent : ClassTag, Err](behavior: AggregateRootBehavior[S, Cmd, Evt, Err], factory: AggregateRootActorFactory)(implicit contract: Aux[O, Cmd, Evt, Err], officeInfo: OfficeInfo[O], eir: EntityIdResolver[O], sr: ShardIdResolver[O], of: OfficeFactory[O]): OfficePath[O] = {
      val propsFactory = BusinessEntityActorFactory[O](factory.inactivityTimeout) { pc =>
        Props(factory.create(pc, behavior))
      }
      val ref = OfficeFactory[O].getOrCreate(officeInfo.name, propsFactory, eir)
      OfficePath[O](ref.path)
    }
  }
}
