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

object BusinessEntityActorFactory {
  def apply[O](inactivityTimeout0: Duration)(f: PassivationConfig => Props): BusinessEntityActorFactory[O] = new BusinessEntityActorFactory[O] {
    override def props(pc: PassivationConfig): Props = f(pc)

    override def inactivityTimeout: Duration = inactivityTimeout0
  }
}
