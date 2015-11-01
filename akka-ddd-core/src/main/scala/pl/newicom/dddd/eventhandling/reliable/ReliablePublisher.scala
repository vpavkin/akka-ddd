package pl.newicom.dddd.eventhandling.reliable

import akka.actor._
import akka.persistence.AtLeastOnceDelivery.{UnconfirmedDelivery, UnconfirmedWarning}
import akka.persistence._
import pl.newicom.dddd.aggregate._
import pl.newicom.dddd.delivery.protocol.alod.Processed
import pl.newicom.dddd.eventhandling.EventPublisher
import pl.newicom.dddd.messaging.MetaData.DeliveryId
import pl.newicom.dddd.messaging.event.{DomainEventMessage, EventMessage}

import scala.collection.immutable.Seq
import scala.concurrent.duration._
import scala.reflect.ClassTag

trait ReliablePublisher[S, O, Cm <: Command, Ev <: DomainEvent, Er] extends PersistentActor with EventPublisher[Ev] with AtLeastOnceDelivery {
  this: AggregateRootActor[S, O, Cm, Ev, Er] =>

  implicit def system: ActorSystem = context.system

  def target: ActorPath

  override def redeliverInterval = 30.seconds
  override def warnAfterNumberOfUnconfirmedAttempts = 15

  override def publish(em: DomainEventMessage[Ev]) {
    deliver(target)(deliveryId => em.withMetaAttribute(DeliveryId, deliveryId))
  }

  abstract override def receiveRecover: Receive = {
    case event: EventMessage[Ev] =>
      super.receiveRecover(event)
      publish(toDomainEventMessage(event))

    case Processed(deliveryId, _) =>
      confirmDelivery(deliveryId)
  }

  abstract override def receiveCommand: Receive = {
    case receipt @ Processed(deliveryId, _) =>
      persist(receipt) {
        _ => confirmDelivery(deliveryId)
      }
    case UnconfirmedWarning(unconfirmedDeliveries) =>
      receiveUnconfirmedDeliveries(unconfirmedDeliveries)
    case c => super.receiveCommand(c)
  }

  def receiveUnconfirmedDeliveries(deliveries: Seq[UnconfirmedDelivery]): Unit = {
    // TODO it should be possible define compensation action that will be triggered from here
    // If compensation applied, unconfirmed deliveries should be confirmed: 
    //unconfirmedDeliveries.foreach(ud => confirmDelivery(ud.deliveryId))
  }
}
