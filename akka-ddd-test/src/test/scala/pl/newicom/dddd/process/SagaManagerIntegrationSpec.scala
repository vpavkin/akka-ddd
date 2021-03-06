package pl.newicom.dddd.process

import akka.actor._
import akka.testkit.TestProbe
import pl.newicom.dddd.actor.{BusinessEntityActorFactory, PassivationConfig}
import pl.newicom.dddd.aggregate._
import pl.newicom.dddd.delivery.protocol.Processed
import pl.newicom.dddd.eventhandling.LocalEventPublisher
import pl.newicom.dddd.office.LocalOffice._
import pl.newicom.dddd.process.SagaManagerIntegrationSpec._
import pl.newicom.dddd.process.SagaSupport.{SagaEventSupplyFactory, registerSaga}
import pl.newicom.dddd.persistence.SaveSnapshotRequest
import pl.newicom.dddd.test.dummy
import pl.newicom.dddd.test.dummy.DummyAggregateRoot._
import pl.newicom.dddd.test.dummy.DummySaga.{DummySagaConfig, EventApplied}
import pl.newicom.dddd.test.dummy.{DummyAggregateRoot, DummySaga}
import pl.newicom.dddd.test.support.IntegrationTestConfig.integrationTestSystem
import pl.newicom.dddd.test.support.OfficeSpec
import pl.newicom.eventstore.EventstoreStream$
import scala.concurrent.duration._

object SagaManagerIntegrationSpec {

  case object GetNumberOfUnconfirmed

  implicit def actorFactory(implicit it: Duration = 1.minute): BusinessEntityActorFactory[DummyOffice] =
    new BusinessEntityActorFactory[DummyOffice] {
      override def props(pc: PassivationConfig): Props = Props(new DummyAggregateRoot with LocalEventPublisher[DummyEvent])
      override def inactivityTimeout: Duration = it
    }


  implicit def actorSystem = integrationTestSystem("SagaManagerIntegrationSpec")
}

/**
 * Requires EventStore to be running on localhost!
 */



class SagaManagerIntegrationSpec extends OfficeSpec[DummyOffice](Some(integrationTestSystem("SagaManagerIntegrationSpec"))) {

  override val shareAggregateRoot = true

  def dummyId = aggregateId

  implicit lazy val testSagaConfig = new DummySagaConfig(s"${DummyAggregateRoot.DummyOffice.info.name}-$dummyId")

  implicit def sagaManagerFactory[S <: Saga[_, _]]: SagaEventSupplyFactory = (sagaConfig, sagaOffice) => {
    new SagaEventSupply(sagaConfig, sagaOffice) with EventstoreStream {
      override def redeliverInterval = 1.seconds
      override def receiveCommand: Receive = myReceive.orElse(super.receiveCommand)

      def myReceive: Receive = {
        case GetNumberOfUnconfirmed => sender() ! numberOfUnconfirmed
      }

    }
  }


  val sagaProbe = TestProbe()
  system.eventStream.subscribe(sagaProbe.ref, classOf[EventApplied])
  ignoreMsg({ case Processed(_, _) => true })

  "SagaManager" should {

    var sagaManager, sagaOffice: ActorRef = null

    "deliver events to a saga office" in {
      // given
      given {
        List(
          CreateDummy(dummyId, "name", "description", 0),
          ChangeValue(dummyId, 1)
        )
      }
      .when {
        ChangeValue(dummyId, 2)
      }
      .expect { c =>
        ValueChanged(dummyId, c.value, 2L)
      }

      // when
      val (so, sm) = registerSaga[DummySaga]
      sagaManager = sm; sagaOffice = so

      // then
      expectNumberOfEventsAppliedBySaga(2)
      expectNoUnconfirmedMessages(sagaManager)
    }

    "persist unconfirmed events" in {
      // given
      ensureActorUnderTestTerminated(sagaManager) // stop events delivery
      when {
        ChangeValue(dummyId, 3) // bump counter by 1, DummySaga should accept this event
      }
      .expect { c =>
        ValueChanged(dummyId, c.value, 3L)
      }

      when {
        ChangeValue(dummyId, 5) // bump counter by 2, DummySaga should not accept this event
      }
      .expect { c =>
        ValueChanged(dummyId, c.value, 4L)
      }

      // when
      sagaManager = registerSaga[DummySaga](sagaOffice) // start events delivery, number of events to be delivered to DummySaga is 2

      // then
      expectNumberOfEventsAppliedBySaga(1)
      expectNumberOfUnconfirmedMessages(sagaManager, 1) // single unconfirmed event: ValueChanged(_, 5)
    }

    "redeliver unconfirmed events to a saga office" in {
      // given
      ensureActorUnderTestTerminated(sagaManager)
      when {
        ChangeValue(dummyId, 4)
      }
      .expect { c =>
        ValueChanged(dummyId, c.value, 5L)
      }

      // when
      sagaManager = registerSaga[DummySaga](sagaOffice)

      // then
      expectNumberOfEventsAppliedBySaga(2)
      expectNoUnconfirmedMessages(sagaManager)

    }
  }

  def expectNumberOfEventsAppliedBySaga(expectedNumberOfEvents: Int): Unit = {
    for (i <- 1 to expectedNumberOfEvents) {
      sagaProbe.expectMsgClass(classOf[EventApplied])
    }
  }

  def expectNoUnconfirmedMessages(sagaManager: ActorRef): Unit = {
    expectNumberOfUnconfirmedMessages(sagaManager, 0)
  }

  def expectNumberOfUnconfirmedMessages(sagaManager: ActorRef, expectedNumberOfMessages: Int): Unit = within(3.seconds) {
    sagaManager ! SaveSnapshotRequest
    awaitAssert {
      sagaManager ! GetNumberOfUnconfirmed
      expectMsg(expectedNumberOfMessages)
    }
  }
}
