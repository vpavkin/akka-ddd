package pl.newicom.dddd.view

import pl.newicom.dddd.aggregate.DomainEvent
import pl.newicom.dddd.messaging.event.{DomainEventMessage, EventMessage}

import scala.concurrent.Future

abstract class ViewHandler[-E <: DomainEvent, O](val vuConfig: ViewUpdateConfig[O]) {

  def handle(eventMessage: DomainEventMessage[E], eventNumber: Long): Future[Unit]

  def lastEventNumber: Future[Option[Long]]

  protected def viewName = vuConfig.viewName

}
