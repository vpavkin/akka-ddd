package pl.newicom.dddd.delivery

import akka.actor.{ActorLogging, ActorPath}
import akka.persistence.AtLeastOnceDelivery.AtLeastOnceDeliverySnapshot
import akka.persistence._
import pl.newicom.dddd.delivery.protocol.Delivered
import pl.newicom.dddd.messaging.Message
import pl.newicom.dddd.persistence.SaveSnapshotRequest

case class DeliveryStateSnapshot(state: DeliveryState, alodSnapshot: AtLeastOnceDeliverySnapshot)

case class TargetedMessage(target: ActorPath, deliveryId: Long, message: Message)

trait AtLeastOnceDeliverySupport extends PersistentActor with AtLeastOnceDelivery with ActorLogging {

  private var deliveryState: DeliveryState = InitialState

  def recoveryCompleted(): Unit

  def lastSentDeliveryId: Option[Long] = deliveryState.lastSentOpt

  def unconfirmedNumber: Int = deliveryState.unconfirmedNumber

  def deliver(destination: ActorPath, msg: Message, deliveryId: Long): Unit =
    persist(TargetedMessage(destination, deliveryId, msg.withDeliveryId(deliveryId)))(updateStateWithMessage)

  def deliveryIdToMessage(msg: TargetedMessage): Long ⇒ Any = { akkaDeliveryId =>
    log.debug("Delivering [{}]", msg)
    deliveryState = deliveryState.withSent(akkaDeliveryId, msg.deliveryId)
    msg.message
  }

  def updateStateWithMessage(targetedMessage: TargetedMessage): Unit = {
    deliver(targetedMessage.target)(deliveryIdToMessage(targetedMessage))
  }

  def deliveryStateReceive: Receive = {
    case receipt: Delivered =>
      persist(receipt)(updateStateWithDelivery)

    case SaveSnapshotRequest =>
      val snapshot = new DeliveryStateSnapshot(deliveryState, getDeliverySnapshot)
      log.debug(s"Saving snapshot: $snapshot")
      saveSnapshot(snapshot)

    case SaveSnapshotSuccess(metadata) =>
      log.debug("Snapshot saved successfully")

    case f @ SaveSnapshotFailure(metadata, reason) =>
      log.error(s"$f")
      throw reason
  }

  def updateStateWithDelivery(receipt: Delivered): Unit = {
    val deliveryId = receipt.deliveryId
    deliveryState.internalDeliveryId(deliveryId).foreach { internalDeliveryId =>
      log.debug(s"[DELIVERY-ID: $internalDeliveryId] - Delivery confirmed")
      if (confirmDelivery(internalDeliveryId)) {
        deliveryState = deliveryState.withDelivered(deliveryId)
        deliveryConfirmed(internalDeliveryId)
      }
    }
  }

  override def receiveRecover: Receive = {
    case RecoveryCompleted  =>
      log.debug("Recovery completed")
      recoveryCompleted()

    case SnapshotOffer(metadata, DeliveryStateSnapshot(dState, alodSnapshot)) =>
      setDeliverySnapshot(alodSnapshot)
      deliveryState = dState
      log.debug(s"Snapshot restored: $deliveryState")

    case msg: TargetedMessage =>
      updateStateWithMessage(msg)
  }

  def deliveryConfirmed(deliveryId: Long): Unit = ()
}
