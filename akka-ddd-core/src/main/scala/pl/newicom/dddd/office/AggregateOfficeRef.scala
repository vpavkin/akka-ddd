package pl.newicom.dddd.office

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.{AskableActorRef, ask}
import akka.util.Timeout
import pl.newicom.dddd.aggregate.{DomainEvent, Command}
import pl.newicom.dddd.delivery.protocol.Processed
import pl.newicom.dddd.messaging.command.CommandMessage

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

sealed trait Response[+Er]

object Response {
  def accepted[R]: Response[R] = Accepted
  def rejected[R](rejection: R): Response[R] = Rejected(rejection)
  def internalError[R](ex: Throwable): Response[R] = InternalError(ex)

  case object Accepted extends Response[Nothing]
  case class Rejected[+R](rejection: R) extends Response[R]
  case class InternalError(ex: Throwable) extends Response[Nothing]
}

abstract class AggregateOfficeRef[O, C <: Command, E <: DomainEvent, R : ClassTag](implicit contract: AggregateContract.Aux[O, C, E, R]) extends Serializable {
  private [dddd] def value: ActorRef
  def process(command: C)(implicit timeout: Timeout, ec: ExecutionContext): Future[Response[R]]
}

object AggregateOfficeRef {
  trait AggregateOfficeRefCreation[O] {
    def apply[C <: Command, E <: DomainEvent, R : ClassTag](v: ActorRef)(implicit contract: AggregateContract.Aux[O, C, E, R]): AggregateOfficeRef[O, C, E, R]
  }
  private [dddd] def apply[O] = new AggregateOfficeRefCreation[O] {
    override def apply[C <: Command, E <: DomainEvent, R : ClassTag](ref: ActorRef)(implicit contract: AggregateContract.Aux[O, C, E, R]): AggregateOfficeRef[O, C, E, R] = new AggregateOfficeRef[O, C, E, R] {
      override def value: ActorRef = ref

      override def process(command: C)(implicit timeout: Timeout, ec: ExecutionContext): Future[Response[R]] = {
        (value ? CommandMessage(command)).mapTo[Processed].map(_.result)
          .map {
            case Some(rejection: R) => Response.rejected(rejection)
            case None => Response.accepted[R]
          }.recover {
          case ex => Response.internalError[R](ex)
        }
      }
    }
  }
}
