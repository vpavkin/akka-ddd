package pl.newicom.dddd.cluster


import akka.actor._
import akka.cluster.client.ClusterClientReceptionist
import akka.cluster.sharding.{ClusterShardingSettings, ClusterSharding}
import akka.cluster.sharding.ShardRegion.Passivate
import pl.newicom.dddd.actor.{BusinessEntityActorFactory, PassivationConfig}
import pl.newicom.dddd.aggregate._
import pl.newicom.dddd.office.{OfficeInfo, OfficeFactory}

import scala.reflect.ClassTag

trait GlobalOffice {

  implicit def globalOfficeFactory[S, O]
  (implicit
   system: ActorSystem,
   tc: AggregateRoot[S, O],
   sr: ShardResolution[S],
   entityFactory: BusinessEntityActorFactory[S],
   officeInfo: OfficeInfo[O],
    s: ClassTag[S]
  ): OfficeFactory[S] = {
    new OfficeFactory[S] {

      def officeName: EntityId = officeInfo.name

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