package pl.newicom.dddd.cluster

import akka.cluster.sharding.ShardRegion._
import pl.newicom.dddd.cluster.ShardResolution._
import pl.newicom.dddd.aggregate.Command
import pl.newicom.dddd.messaging.EntityMessage
import pl.newicom.dddd.messaging.command.CommandMessage
import pl.newicom.dddd.messaging.correlation.EntityIdResolution
import pl.newicom.dddd.messaging.correlation.EntityIdResolution.EntityIdResolver

object ShardResolution {
  type ShardResolutionStrategy = EntityIdResolver => ExtractShardId
}

trait ShardResolution[A] {
  def shardResolutionStrategy: ShardResolutionStrategy
  def idExtractor(entityIdResolver: EntityIdResolver): ExtractEntityId = {
    case em: EntityMessage => (entityIdResolver(em), em)
    case c: Command => (entityIdResolver(c), CommandMessage(c))
  }
}

