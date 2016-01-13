package pl.newicom.dddd.cluster

import pl.newicom.dddd.messaging.correlation.EntityIdResolver

case class DefaultShardIdResolver[A](numberOfNodes: Int) extends ShardIdResolver[A] {
  def resolveShardId(entityIdResolver: EntityIdResolver[A]) = {
    case msg => (Math.abs(entityIdResolver.resolveEntityId.andThen(_._1)(msg).hashCode) % (numberOfNodes * 10)).toString
  }
}
