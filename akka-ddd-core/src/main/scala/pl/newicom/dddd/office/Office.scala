package pl.newicom.dddd.office

import akka.actor.ActorRef
import pl.newicom.dddd.actor.BusinessEntityActorFactory
import pl.newicom.dddd.aggregate._
import pl.newicom.dddd.cluster.ShardResolution
import pl.newicom.dddd.messaging.correlation.EntityIdResolution

object Office {

  def office[A <: BusinessEntity : BusinessEntityActorFactory : EntityIdResolution : OfficeFactory]: ActorRef = {
    implicitly[OfficeFactory[A]].getOrCreate
  }

  trait Office[O, S] {
    def apply[Cm <: Command, Ev <: DomainEvent, Er]()(implicit
                                                     tc: AggregateState.Aux[S, Cm, Ev, Er],
                                                     factory: AggregateRootActorFactory[AggregateRoot[S, O, Cm, Ev, Er]],
                                                     shardResolution: ShardResolution[AggregateRoot[S, O, Cm, Ev, Er]],
                                                     officeInfo: OfficeInfo[O],
                                                     officeFactory: OfficeFactory[AggregateRoot[S, O, Cm, Ev, Er]]): ActorRef
  }

  def office[O, S] = new Office[O, S] {
    override def apply[Cm <: Command, Ev <: DomainEvent, Er]()(implicit
                                                           tc: AggregateState.Aux[S, Cm, Ev, Er],
                                                           factory: AggregateRootActorFactory[AggregateRoot[S, O, Cm, Ev, Er]],
                                                           shardResolution: ShardResolution[AggregateRoot[S, O, Cm, Ev, Er]],
                                                           officeInfo: OfficeInfo[O],
                                                           officeFactory: OfficeFactory[AggregateRoot[S, O, Cm, Ev, Er]]): ActorRef =
    officeFactory.getOrCreate
  }

}
