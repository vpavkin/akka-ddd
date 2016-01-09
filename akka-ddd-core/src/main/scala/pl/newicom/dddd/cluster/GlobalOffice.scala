package pl.newicom.dddd.cluster


import akka.actor._
import akka.cluster.client.ClusterClientReceptionist
import akka.cluster.sharding.{ClusterShardingSettings, ClusterSharding}
import akka.cluster.sharding.ShardRegion.Passivate
import pl.newicom.dddd.actor.{BusinessEntityActorFactory, PassivationConfig}
import pl.newicom.dddd.aggregate._
import pl.newicom.dddd.messaging.correlation.EntityIdResolution
import pl.newicom.dddd.office.{OfficeInfo, OfficeFactory}

import scala.reflect.ClassTag
import scala.util.Try

trait GlobalOffice {

  implicit def globalOfficeFactory[O]
  (implicit
   system: ActorSystem,
   shardResolution: ShardResolution[O]
  ): OfficeFactory[O] = {
    new OfficeFactory[O] {

      override def getOrCreate(name: String, entityFactory: BusinessEntityActorFactory[O], entityIdResolution: EntityIdResolution[O]): ActorRef = {
        region(name).getOrElse(createRegion(name, entityFactory, entityIdResolution))
      }

      private def region(name: String): Option[ActorRef] = Try(ClusterSharding(system).shardRegion(name)).toOption

      private def createRegion(name: String, entityFactory: BusinessEntityActorFactory[O], entityIdResolution: EntityIdResolution[O]): ActorRef = {
        val entityProps = entityFactory.props(new PassivationConfig(Passivate(PoisonPill), entityFactory.inactivityTimeout))
        val region = ClusterSharding(system).start(
          typeName = name,
          entityProps = entityProps,
          settings = ClusterShardingSettings(system),
          extractEntityId = shardResolution.idExtractor(entityIdResolution.entityIdResolver),
          extractShardId = shardResolution.shardResolutionStrategy(entityIdResolution.entityIdResolver)
        )
        ClusterClientReceptionist(system).registerService(region)
        region
      }

    }

  }

}