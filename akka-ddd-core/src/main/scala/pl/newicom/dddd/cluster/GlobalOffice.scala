package pl.newicom.dddd.cluster


import akka.actor._
import akka.cluster.client.ClusterClientReceptionist
import akka.cluster.sharding.ShardRegion.Passivate
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import pl.newicom.dddd.actor.{BusinessEntityActorFactory, PassivationConfig}
import pl.newicom.dddd.messaging.correlation.EntityIdResolver
import pl.newicom.dddd.office.OfficeFactory

import scala.util.Try

trait GlobalOffice {

  implicit def globalOfficeFactory[O](implicit system: ActorSystem, shardResolution: ShardIdResolver[O]): OfficeFactory[O] = new OfficeFactory[O] {
    override def getOrCreate(name: String, entityFactory: BusinessEntityActorFactory[O], entityIdResolution: EntityIdResolver[O]): ActorRef = {
      region(name).getOrElse(createRegion(name, entityFactory, entityIdResolution))
    }

    private def region(name: String): Option[ActorRef] = Try(ClusterSharding(system).shardRegion(name)).toOption

    private def createRegion(name: String, entityFactory: BusinessEntityActorFactory[O], entityIdResolver: EntityIdResolver[O]): ActorRef = {
      val entityProps = entityFactory.props(new PassivationConfig(Passivate(PoisonPill), entityFactory.inactivityTimeout))
      val region = ClusterSharding(system).start(
        typeName = name,
        entityProps = entityProps,
        settings = ClusterShardingSettings(system),
        extractEntityId = entityIdResolver.resolveEntityId,
        extractShardId = shardResolution.resolveShardId(entityIdResolver)
      )
      ClusterClientReceptionist(system).registerService(region)
      region
    }

  }
}