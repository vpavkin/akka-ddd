package pl.newicom.dddd.office

import akka.actor.ActorRef
import pl.newicom.dddd.actor.BusinessEntityActorFactory
import pl.newicom.dddd.messaging.correlation.EntityIdResolution

object Office {
  def office[O : OfficeInfo : BusinessEntityActorFactory : EntityIdResolution : OfficeFactory]: ActorRef = {
    implicitly[OfficeFactory[O]].getOrCreate
  }
}
