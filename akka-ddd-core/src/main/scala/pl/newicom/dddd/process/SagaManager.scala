package pl.newicom.dddd.process

import akka.actor.ActorPath
import pl.newicom.dddd.messaging.event.EventStreamSubscriber
import pl.newicom.dddd.office.OfficeInfo

import scala.concurrent.duration._

class SagaManager(sagaConfig: SagaConfig, sagaOffice: ActorPath) extends Receptor {
  this: EventStreamSubscriber =>

  implicit val of = new OfficeInfo[Saga[_, _]] {
    override def name: String = sagaConfig.name
    override def isSagaOffice: Boolean = true
  }

  lazy val config: ReceptorConfig =
    ReceptorBuilder().
      reactTo[Saga[_, _]].
      propagateTo(sagaOffice)
  
  override def redeliverInterval = 30.seconds
  override def warnAfterNumberOfUnconfirmedAttempts = 15


}
