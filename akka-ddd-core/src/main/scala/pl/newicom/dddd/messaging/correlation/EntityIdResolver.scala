package pl.newicom.dddd.messaging.correlation

import akka.cluster.sharding.ShardRegion.ExtractEntityId
import pl.newicom.dddd.aggregate.EntityId

object EntityIdResolver {
  type ResolveEntityId = PartialFunction[Any, EntityId]
}

trait EntityIdResolver[A] {
  def resolveEntityId: ExtractEntityId
}
