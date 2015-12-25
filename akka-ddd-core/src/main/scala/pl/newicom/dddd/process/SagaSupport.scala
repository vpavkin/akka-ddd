package pl.newicom.dddd.process

import akka.actor.{ActorPath, ActorRef, Props}
import pl.newicom.dddd.actor.{BusinessEntityActorFactory, CreationSupport}
import pl.newicom.dddd.messaging.correlation.EntityIdResolution
import pl.newicom.dddd.office.{Office, OfficeFactory}

object SagaSupport {

  /**
   * Responsible of creating [[SagaManager]] using provided [[SagaConfig]] and path to saga office
   */
  type SagaManagerFactory[S <: Saga[_]] = (SagaConfig[S], ActorPath) => SagaManager[S]

  implicit def defaultCaseIdResolution[A <: Saga[_]](): EntityIdResolution[A] = new EntityIdResolution[A]

  def registerSaga[S <: Saga[_] : SagaConfig](sagaOffice: ActorRef)(implicit cs: CreationSupport, smf: SagaManagerFactory[S]): ActorRef = {
    val sagaOfficePath = sagaOffice.path
    val sagaConfig: SagaConfig[S] = implicitly[SagaConfig[S]]

    val sagaManagerProps = Props[SagaManager[S]](smf(sagaConfig, sagaOfficePath))
    val sagaManager = cs.createChild(sagaManagerProps, s"SagaManager-${sagaConfig.bpsName}")

    sagaManager
  }

  def registerSaga[A <: Saga[_] : SagaConfig : EntityIdResolution : OfficeFactory : SagaActorFactory]
    (implicit cs: CreationSupport, smf: SagaManagerFactory[A]): (ActorRef, ActorRef) = {
    
    val sagaOffice : ActorRef = Office.office[A]
    (sagaOffice, registerSaga[A](sagaOffice))
  }

}
