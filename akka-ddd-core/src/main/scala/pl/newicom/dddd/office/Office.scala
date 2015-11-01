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

//  trait Office[O] {
//    def apply[S]()(implicit
//                   tc: AggregateRoot[S, O],
//                   factory: AggregateRootActorFactory[S],
//                   shardResolution: ShardResolution[S],
//                   officeInfo: OfficeInfo[O],
//                   officeFactory: OfficeFactory[S]): ActorRef
//  }
//
//  def office[O] = new Office[O] {
//    override def apply[S]()(implicit
//                            tc: AggregateRoot[S, O],
//                            factory: AggregateRootActorFactory[S],
//                            shardResolution: ShardResolution[S],
//                            officeInfo: OfficeInfo[O],
//                            officeFactory: OfficeFactory[S]): ActorRef =
//    officeFactory.getOrCreate
//  }

}
