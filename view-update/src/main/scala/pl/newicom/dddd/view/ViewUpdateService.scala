package pl.newicom.dddd.view

import akka.{Done, NotUsed}
import akka.actor.Status.Failure
import akka.actor.SupervisorStrategy._
import akka.actor._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, Source, Sink, RunnableGraph}
import eventstore.EventNumber.Exact
import eventstore._
import pl.newicom.dddd.aggregate.DomainEvent
import pl.newicom.dddd.messaging.event.EventSource.EventReceived
import pl.newicom.dddd.messaging.event.{EventSource, OfficeEventStream}
import pl.newicom.dddd.office.OfficeInfo
import pl.newicom.dddd.view.ViewUpdateInitializer.ViewUpdateInitException
import pl.newicom.dddd.view.ViewUpdateService._
import pl.newicom.eventstore.{StreamNameResolver, EventstoreSerializationSupport}
import akka.pattern.pipe

import scala.concurrent.{ExecutionContext, Future}

object ViewUpdateService {
  object EnsureViewStoreAvailable

  case class InitiateViewUpdate(esCon: EsConnection)
  case class ViewUpdateInitiated(esCon: EsConnection)

  case class ViewUpdateConfigured[O](viewUpdate: ViewUpdate[O])

  case class ViewUpdate[O](officeInfo: OfficeInfo[O], lastEventNr: Option[Long], runnable: RunnableGraph[Future[Unit]]) {
    override def toString =  s"ViewUpdate(officeName = ${officeInfo.name}, lastEventNr = $lastEventNr)"
  }

}

abstract class ViewUpdateService[-E <: DomainEvent, O](eventSource: EventSource[EventReceived[E], NotUsed]) extends Actor with ActorLogging {

  type VUConfig <: ViewUpdateConfig[O]

  def system = context.system

  implicit val ec: ExecutionContext = context.dispatcher

  implicit val materializer: ActorMaterializer = ActorMaterializer()

  def vuConfigs: Seq[VUConfig]

  def viewHandler(config: VUConfig): ViewHandler[E, O]

  def ensureViewStoreAvailable: Future[Unit]

  /**
   * Overridable initialization logic
   */
  def onViewUpdateInit(esCon: EsConnection): Future[ViewUpdateInitiated] =
    Future.successful(ViewUpdateInitiated(esCon))

  /**
   * Restart ViewUpdateInitializer until it successfully obtains connection to event store and view store
   * During normal processing escalate all exceptions so that feeding is restarted
   */
  override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
    case _: ActorKilledException               => Stop
    case _: ActorInitializationException       => Stop
    case _: ViewUpdateInitException            => Restart
    case _                                     => Escalate
  }


  @scala.throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
    context.actorOf(Props(new ViewUpdateInitializer(self)))
  }

  override def receive: Receive = {
    case InitiateViewUpdate(esCon) =>
      onViewUpdateInit(esCon) pipeTo self

    case ViewUpdateInitiated(esCon) =>
      log.debug("Initiated.")
      vuConfigs.map(viewUpdate(esCon, _)).foreach(_.pipeTo(self))

    case vu @ ViewUpdate(_, _, runnable) =>
        log.debug(s"Starting: $vu")
        runnable.run() pipeTo self

    case Failure(ex) =>
      throw ex

    case EnsureViewStoreAvailable =>
      ensureViewStoreAvailable pipeTo sender()

    case unexpected =>
      throw new RuntimeException(s"Unexpected message received: $unexpected")
  }


  def viewUpdate(esCon: EsConnection, vuConfig: VUConfig): Future[ViewUpdate[O]] = {
    val handler = viewHandler(vuConfig)
    val officeInfo = vuConfig.officeInfo
    handler.lastEventNumber.map { lastEvtNrOpt =>
      val graph = eventSource(OfficeEventStream(officeInfo), lastEvtNrOpt)
        .mapAsync(1) { event =>
          handler.handle(event.em, event.position)
        }
        .toMat(Sink.ignore)(Keep.right)
        .mapMaterializedValue(_.map(_ => ()))
      ViewUpdate(officeInfo, lastEvtNrOpt, graph)
    }
  }
}
