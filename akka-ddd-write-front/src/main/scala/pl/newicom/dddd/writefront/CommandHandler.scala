package pl.newicom.dddd.writefront

import akka.actor.Actor
import akka.pattern.ask
import akka.util.Timeout
import pl.newicom.dddd.aggregate.{Command, DomainEvent}
import pl.newicom.dddd.delivery.protocol.Processed
import pl.newicom.dddd.messaging.command.CommandMessage
import pl.newicom.dddd.office.{OfficeContract, OfficeInfo}

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag
import scala.util.{Failure, Success}
import scalaz.\/

sealed trait Response[+Er]

object Response {
  def accepted[Er]: Response[Er] = Accepted
  def domainError[Er](error: Er): Response[Er] = DomainError(error)
  def internalError[Er](ex: Throwable): Response[Er] = InternalError(ex)

  case object Accepted extends Response[Nothing]
  case class DomainError[+Er](error: Er) extends Response[Er]
  case class InternalError(ex: Throwable) extends Response[Nothing]
}

trait CommandHandler extends GlobalOfficeClientSupport {
  this: Actor =>

  trait Handle[O] {
    def apply[Er : ClassTag, Cm <: Command, Ev <: DomainEvent](command: Cm)
                                                              (implicit t: Timeout, ec: ExecutionContext, o: OfficeInfo[O], contract: OfficeContract.Aux[O, Cm, Ev, Er]): Future[Response[Er]]
  }

  def handle[O] = new Handle[O] {
    override def apply[Er: ClassTag, Cm <: Command, Ev <: DomainEvent]
    (command: Cm)
    (implicit t: Timeout, ec: ExecutionContext, o: OfficeInfo[O], contract: OfficeContract.Aux[O, Cm, Ev, Er]): Future[Response[Er]] = {
      office(o.name).ask(CommandMessage(command)).map {
        case Processed(_, Success(result: \/[_, _])) =>
          result.leftMap {
            case e: Er => Response.domainError[Er](e)
          }.map {
            _ => Response.accepted[Er]
          }.merge
        case Processed(_, Failure(ex)) =>
          Response.internalError[Er](ex)
      }
    }
  }
}