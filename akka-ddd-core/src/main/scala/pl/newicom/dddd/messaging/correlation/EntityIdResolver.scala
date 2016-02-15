package pl.newicom.dddd.messaging.correlation

import akka.cluster.sharding.ShardRegion.ExtractEntityId
import pl.newicom.dddd.aggregate._
import pl.newicom.dddd.messaging.command.CommandMessage
import pl.newicom.dddd.messaging.event.EventMessage
import pl.newicom.dddd.process.Saga
import pl.newicom.dddd.process.typesafe.InjectAny
import shapeless.Coproduct

private [dddd] sealed trait EntityIdResolver[A] {
  def resolveEntityId: ExtractEntityId
}

private [dddd] class AggregateIdResolver[A] extends EntityIdResolver[A] {
  override def resolveEntityId: ExtractEntityId = {
    case cm: CommandMessage[_] => (cm.command.aggregateId, cm)
  }
}

private [dddd] class SagaIdResolver[In <: Coproduct, State](f: In => EntityId)(implicit In: InjectAny[In]) extends EntityIdResolver[Saga[In, State]] {
  override def resolveEntityId: ExtractEntityId = {
    case em @ EventMessage(_, In(in)) => (f(in), em)
  }
}