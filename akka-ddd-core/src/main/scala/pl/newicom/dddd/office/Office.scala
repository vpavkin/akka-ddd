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

  trait Office[O] {
    def apply[S, Cm <: Command, Ev <: DomainEvent, Er]()(implicit
                                                         tc: AggregateRoot.Aux[S, O, Cm, Ev, Er],
                                                         factory: AggregateRootActorFactory[AggregateRootActor[S, O, Cm, Ev, Er]],
                                                         shardResolution: ShardResolution[AggregateRootActor[S, O, Cm, Ev, Er]],
                                                         officeInfo: OfficeInfo[O],
                                                         officeFactory: OfficeFactory[AggregateRootActor[S, O, Cm, Ev, Er]]): ActorRef
  }

  def office[O] = new Office[O] {
    override def apply[S, Cm <: Command, Ev <: DomainEvent, Er]()(implicit
                                                                  tc: AggregateRoot.Aux[S, O, Cm, Ev, Er],
                                                                  factory: AggregateRootActorFactory[AggregateRootActor[S, O, Cm, Ev, Er]],
                                                                  shardResolution: ShardResolution[AggregateRootActor[S, O, Cm, Ev, Er]],
                                                                  officeInfo: OfficeInfo[O],
                                                                  officeFactory: OfficeFactory[AggregateRootActor[S, O, Cm, Ev, Er]]): ActorRef =
    officeFactory.getOrCreate
  }

}
