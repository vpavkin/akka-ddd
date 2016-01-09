package pl.newicom.dddd.process

import akka.actor.{ActorPath, ActorRef, Props}
import pl.newicom.dddd.actor.{PassivationConfig, BusinessEntityActorFactory, CreationSupport}
import pl.newicom.dddd.aggregate.EntityId
import pl.newicom.dddd.cluster.ShardResolution
import pl.newicom.dddd.messaging.correlation.EntityIdResolution
import pl.newicom.dddd.messaging.correlation.EntityIdResolution.EntityIdResolver
import pl.newicom.dddd.office.{OfficeInfo, Office, OfficeFactory}
import pl.newicom.dddd.process.typesafe.{InjectAny, EventReaction}
import shapeless.Coproduct
import shapeless.ops.coproduct.Folder

import scala.concurrent.duration.Duration
import scala.reflect.ClassTag


import scala.concurrent.duration._

object SagaSupport {

  type SagaManagerFactory = (SagaConfig, ActorPath) => SagaManager

  def registerSaga(config: SagaConfig)
                  (implicit f1: Folder.Aux[config.resolveId.type, config.Input, EntityId],
                   f2: Folder.Aux[config.receiveEvent.type, config.Input, Option[config.State] => EventDecision],
                   f3: Folder.Aux[config.applyEvent.type, config.Input, Option[config.State] => EventReaction[config.State]],
                   inject: InjectAny[config.Input],
                   officeFactory: OfficeFactory[Saga[_, _]],
                   sagaManagerFactory: SagaManagerFactory,
                   creationSupport: CreationSupport,
                   shardResolution: ShardResolution[Saga[_, _]],
                    stateClassTag: ClassTag[config.State]): Unit = {

    import config._
    implicit val sagaActorFactory = new BusinessEntityActorFactory[Saga[_, _]] {
      override def props(pc: PassivationConfig): Props = Saga.props(pc, config)
      override def inactivityTimeout: Duration = 1.minute
    }

    implicit val officeInfo = new OfficeInfo[Saga[_, _]] {
      override def name: String = config.name
    }

    implicit def defaultCaseIdResolution = new EntityIdResolution[Saga[_, _]] {
      override val entityIdResolver: EntityIdResolver = {
        case inject(in) => in.fold(resolveId)
      }
    }

    val sagaOffice : ActorRef = Office.office[Saga[_, _]]
    val sagaOfficePath = sagaOffice.path

    val sagaManagerProps = Props(sagaManagerFactory(config, sagaOfficePath))
    val sagaManager = creationSupport.createChild(sagaManagerProps, s"SagaManager-$name")
  }

}
