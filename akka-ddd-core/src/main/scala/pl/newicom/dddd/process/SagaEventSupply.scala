package pl.newicom.dddd.process

import akka.actor.ActorPath
import pl.newicom.dddd.aggregate.DomainEvent
import pl.newicom.dddd.messaging.event.EventSource
import pl.newicom.dddd.messaging.event.EventSource.{DemandCallback, EventReceived}
import pl.newicom.dddd.office.OfficeInfo

import scala.concurrent.duration._

class SagaEventSupply(sagaConfig: SagaConfig, sagaOfficePath: ActorPath, eventSource: EventSource[EventReceived[DomainEvent], DemandCallback]) extends Receptor(eventSource) {

  implicit val of = new OfficeInfo[Saga[_, _]] {
    override def name: String = sagaConfig.name
    override def isSagaOffice: Boolean = true
  }

  lazy val config: ReceptorConfig =
    ReceptorConfig.reactTo[Saga[_, _]].propagateTo(sagaOfficePath)
  
  override def redeliverInterval = 30.seconds
  override def warnAfterNumberOfUnconfirmedAttempts = 15
}
