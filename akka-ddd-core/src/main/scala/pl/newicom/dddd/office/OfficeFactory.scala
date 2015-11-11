package pl.newicom.dddd.office

import akka.actor.ActorRef
import pl.newicom.dddd.actor.BusinessEntityActorFactory
import pl.newicom.dddd.aggregate.BusinessEntity
import pl.newicom.dddd.messaging.correlation.EntityIdResolution

import scala.reflect.ClassTag

trait OfficeFactory[A] {
  def getOrCreate: ActorRef

  def officeName: String
}