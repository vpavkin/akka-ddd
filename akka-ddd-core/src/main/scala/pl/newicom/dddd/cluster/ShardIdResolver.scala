package pl.newicom.dddd.cluster

import akka.cluster.sharding.ShardRegion._
import pl.newicom.dddd.messaging.correlation.EntityIdResolver

trait ShardIdResolver[A] {
  def resolveShardId(entityIdResolver: EntityIdResolver[A]): ExtractShardId
}

