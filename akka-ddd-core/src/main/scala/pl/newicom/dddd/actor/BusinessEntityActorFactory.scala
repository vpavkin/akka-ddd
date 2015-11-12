package pl.newicom.dddd.actor

import akka.actor.Props
import pl.newicom.dddd.aggregate.BusinessEntity

import scala.annotation.implicitNotFound
import scala.concurrent.duration.Duration

@implicitNotFound("could not find factory for ${O} \nsee: BusinessEntityActorFactory")
abstract class BusinessEntityActorFactory[O] {
  def props(pc: PassivationConfig): Props
  def inactivityTimeout: Duration
}
