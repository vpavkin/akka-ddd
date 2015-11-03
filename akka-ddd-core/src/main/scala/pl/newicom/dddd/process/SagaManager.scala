package pl.newicom.dddd.process

import akka.actor.ActorPath
import pl.newicom.dddd.aggregate._
import pl.newicom.dddd.messaging.Metadata
import pl.newicom.dddd.messaging.Metadata._
import pl.newicom.dddd.messaging.event.{EventMessage, EventStreamSubscriber}
import pl.newicom.dddd.office.OfficeInfo
import SagaManager._
import scala.concurrent.duration._

class BusinessProcess

object SagaManager {
  implicit def businessProcessInfo(implicit sc: SagaConfig[_]): OfficeInfo[BusinessProcess] = {
    new OfficeInfo[BusinessProcess] {
      def name = sc.name
      override def isSagaOffice = true
    }
  }

}

class SagaManager(sagaConfig: SagaConfig[_], sagaOffice: ActorPath) extends Receptor {
  this: EventStreamSubscriber =>

  implicit val sc: SagaConfig[_] = sagaConfig

  lazy val config: ReceptorConfig =
    ReceptorBuilder().
      reactTo[BusinessProcess].
      propagateTo(sagaOffice)
  
  override def redeliverInterval = 30.seconds
  override def warnAfterNumberOfUnconfirmedAttempts = 15

  override def metaDataProvider(em: EventMessage[DomainEvent]): Option[Metadata] =
    sagaConfig.correlationIdResolver.lift(em.event).map { correlationId =>
      new Metadata(Map(CorrelationId -> correlationId))
    }

}
