package pl.newicom.dddd.office

import akka.actor._
import pl.newicom.dddd.actor.{ActorContextCreationSupport, BusinessEntityActorFactory, Passivate, PassivationConfig}
import pl.newicom.dddd.messaging.EntityMessage
import pl.newicom.dddd.messaging.correlation.EntityIdResolver
import pl.newicom.dddd.utils.UUIDSupport.uuid7

import scala.concurrent.duration._
import scala.reflect.ClassTag

object LocalOffice {

  implicit def localOfficeFactory[A : EntityIdResolver : ClassTag](implicit system: ActorSystem): OfficeFactory[A] = {
    new OfficeFactory[A] {
      override def getOrCreate(name: String, entityFactory: BusinessEntityActorFactory[A], entityIdResolver: EntityIdResolver[A]): ActorRef = {
        system.actorOf(Props(new LocalOffice[A](entityFactory, entityIdResolver)), s"${name}_${uuid7}")
      }
    }
  }
}

class LocalOffice[A : ClassTag](clerkFactory: BusinessEntityActorFactory[A], entityIdResolver: EntityIdResolver[A], inactivityTimeout: Duration = 1.minutes)
  extends ActorContextCreationSupport with Actor with ActorLogging {

  override def aroundReceive(receive: Actor.Receive, msg: Any): Unit = {
    val message = entityIdResolver.resolveEntityId.lift(msg).map(_._2)
    message match {
      case Some(in) => receive.applyOrElse(in, unhandled)
      case None => unhandled(msg)
    }
  }

  def receive: Receive = {
    case Passivate(stopMessage) =>
      dismiss(sender(), stopMessage)
    case msg: EntityMessage =>
      val clerkProps = clerkFactory.props(PassivationConfig(Passivate(PoisonPill), clerkFactory.inactivityTimeout))
      val clerk = assignClerk(clerkProps, resolveEntityId(msg))
      log.debug(s"Forwarding EntityMessage to ${clerk.path}")
      clerk forward msg
  }

  def resolveEntityId(msg: Any) = entityIdResolver.resolveEntityId.andThen(_._1)(msg)

  def assignClerk(caseProps: Props, caseId: String): ActorRef = getOrCreateChild(caseProps, caseId)

  def dismiss(clerk: ActorRef, stopMessage: Any) {
    log.info(s"Passivating $sender()")
    clerk ! stopMessage
  }
}
