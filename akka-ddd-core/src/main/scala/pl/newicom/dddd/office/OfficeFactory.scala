package pl.newicom.dddd.office

import akka.actor.{Props, ActorRef}
import pl.newicom.dddd.actor.{PassivationConfig, BusinessEntityActorFactory}
import pl.newicom.dddd.aggregate.BusinessEntity
import pl.newicom.dddd.cluster.ShardResolution
import pl.newicom.dddd.messaging.correlation.EntityIdResolution

import scala.reflect.ClassTag

trait OfficeFactory[A] {
  def getOrCreate(name: String, entityFactory: BusinessEntityActorFactory[A], entityIdResolution: EntityIdResolution[A]): ActorRef
}

object OfficeFactory {
  def apply[A](implicit instance: OfficeFactory[A]): OfficeFactory[A] = instance
}