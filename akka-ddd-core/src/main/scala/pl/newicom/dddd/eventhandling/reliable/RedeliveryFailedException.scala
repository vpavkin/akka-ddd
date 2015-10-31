package pl.newicom.dddd.eventhandling.reliable

import pl.newicom.dddd.aggregate.DomainEvent
import pl.newicom.dddd.messaging.event.DomainEventMessage

case class RedeliveryFailedException(event: DomainEventMessage[DomainEvent]) extends RuntimeException
