package pl.newicom.dddd.process

import akka.actor.ActorPath
import pl.newicom.dddd.aggregate._
import pl.newicom.dddd.messaging.MetaData
import pl.newicom.dddd.messaging.MetaData._
import pl.newicom.dddd.messaging.event.{EventMessage, EventStreamSubscriber}
import pl.newicom.dddd.office.OfficeInfo
import SagaManager._
import scala.concurrent.duration._

class BusinessProcess

object SagaManager {
  implicit def businessProcessInfo[S <: Saga[_]](implicit sc: SagaConfig[S]): OfficeInfo[BusinessProcess] = {
    new OfficeInfo[BusinessProcess] {
      def name = sc.name
      override def isSagaOffice = true
    }
  }

}

class SagaManager[S <: Saga[_]](sagaConfig: SagaConfig[S], sagaOffice: ActorPath) extends Receptor {
  this: EventStreamSubscriber =>

  implicit val sc: SagaConfig[S] = sagaConfig

  lazy val config: ReceptorConfig =
    ReceptorBuilder().
      reactTo[BusinessProcess].
      propagateTo(sagaOffice)
  
  override def redeliverInterval = 30.seconds
  override def warnAfterNumberOfUnconfirmedAttempts = 15
}
