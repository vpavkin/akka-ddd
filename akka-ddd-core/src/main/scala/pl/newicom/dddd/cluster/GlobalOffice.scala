package pl.newicom.dddd.cluster


import akka.actor._
import akka.cluster.client.ClusterClientReceptionist
import akka.cluster.sharding.{ClusterShardingSettings, ClusterSharding}
import akka.cluster.sharding.ShardRegion.Passivate
import pl.newicom.dddd.actor.{BusinessEntityActorFactory, PassivationConfig}
import pl.newicom.dddd.aggregate._
import pl.newicom.dddd.office.{OfficeInfo, OfficeFactory}

trait GlobalOffice {

  implicit def globalOfficeFactory[S, O, Cm <: Command, Ev <: DomainEvent, Er]
  (implicit
   system: ActorSystem,
   tc: AggregateState.Aux[S, Cm, Ev, Er],
   sr: ShardResolution[AggregateRoot[S, O, Cm, Ev, Er]],
   entityFactory: BusinessEntityActorFactory[AggregateRoot[S, O, Cm, Ev, Er]],
   officeInfo: OfficeInfo[O]
  ): OfficeFactory[AggregateRoot[S, O, Cm, Ev, Er]] = {
    new OfficeFactory[AggregateRoot[S, O, Cm, Ev, Er]] {

      override def officeName: EntityId = officeInfo.name

      val shardSettings = ClusterShardingSettings(system)

      private def region: Option[ActorRef] = {
        try {
          Some(ClusterSharding(system).shardRegion(officeName))
        } catch {
          case ex: IllegalArgumentException => None
        }
      }

      override def getOrCreate: ActorRef = {
        region.getOrElse {
          startSharding(shardSettings)
          region.get
        }
      }



      private def startSharding(shardSettings: ClusterShardingSettings): Unit = {
        val entityProps = entityFactory.props(new PassivationConfig(Passivate(PoisonPill), entityFactory.inactivityTimeout))
        ClusterSharding(system).start(
          typeName = officeName,
          entityProps = entityProps,
          settings = shardSettings,
          extractEntityId = sr.idExtractor,
          extractShardId = sr.shardResolver)

        ClusterClientReceptionist(system).registerService(region.get)

      }

    }

  }

}