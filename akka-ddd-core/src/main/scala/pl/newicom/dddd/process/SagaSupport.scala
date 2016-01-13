package pl.newicom.dddd.process

import akka.actor.{ActorPath, ActorRef, Props}
import pl.newicom.dddd.actor.{BusinessEntityActorFactory, CreationSupport, PassivationConfig}
import pl.newicom.dddd.aggregate.EntityId
import pl.newicom.dddd.cluster.ShardIdResolver
import pl.newicom.dddd.messaging.correlation.{SagaIdResolver, EntityIdResolver}
import pl.newicom.dddd.messaging.correlation.EntityIdResolver.ResolveEntityId
import pl.newicom.dddd.office.{Office, OfficeFactory, OfficeInfo}
import pl.newicom.dddd.process.typesafe.{EventReaction, InjectAny}
import shapeless.Coproduct
import shapeless.ops.coproduct.Folder

import scala.concurrent.duration.{Duration, _}
import scala.reflect.ClassTag

object SagaSupport {

  type SagaEventSupplyFactory = (SagaConfig, ActorPath) => SagaEventSupply
  type SagaOfficeFactory[In <: Coproduct, State] = OfficeFactory[Saga[In, State]]

  def registerSaga(config: SagaConfig)
                  (implicit f1: Folder.Aux[config.resolveId.type, config.Input, EntityId],
                   f2: Folder.Aux[config.receiveEvent.type, config.Input, Option[config.State] => EventDecision],
                   f3: Folder.Aux[config.applyEvent.type, config.Input, Option[config.State] => EventReaction[config.State]],
                   inject: InjectAny[config.Input],
                   officeFactory: SagaOfficeFactory[config.Input, config.State],
                   sagaManagerFactory: SagaEventSupplyFactory,
                   creationSupport: CreationSupport,
                   shardResolution: ShardIdResolver[Saga[config.Input, config.State]],
                   stateClassTag: ClassTag[config.State]): Unit = {

    import config._
    implicit val sagaActorFactory = new BusinessEntityActorFactory[Saga[Input, State]] {
      override def props(pc: PassivationConfig): Props = Saga.props(pc, config)
      override def inactivityTimeout: Duration = 1.minute
    }

    implicit val officeInfo = SagaOfficeInfo[Input, State](name)
    implicit def sagaIdResolution = new SagaIdResolver[Input, State](f1.apply)
    val sagaOffice : ActorRef = Office.office[Saga[Input, State]]
    val sagaManagerProps = Props(sagaManagerFactory(config, sagaOffice.path))
    val sagaManager = creationSupport.createChild(sagaManagerProps, s"SagaManager-$name")
  }

}
