package pl.newicom.dddd.writefront

import akka.actor._
import akka.cluster.client.{ClusterClientSettings, ClusterClient}
import pl.newicom.dddd.aggregate.{DomainEvent, Command}
import pl.newicom.dddd.office.{AggregateContract, OfficeInfo, AggregateOfficeRef}

import scala.collection.mutable
import scala.reflect.ClassTag

trait GlobalOfficeClientSupport {
  this: Actor =>

  def contactPoints: Seq[String]

  private lazy val officeClientMap: mutable.Map[String, ActorRef] = mutable.Map.empty

  private lazy val clusterClient: ActorRef = {
    val initialContacts: Seq[ActorPath] = contactPoints.map {
      case AddressFromURIString(address) ⇒ RootActorPath(address) / "system" / "receptionist"
    }
    system.actorOf(ClusterClient.props(ClusterClientSettings(system).withInitialContacts(initialContacts.toSet)), "clusterClient")
  }

  trait Office[O] {
    def apply[C <: Command, E <: DomainEvent, R : ClassTag]()(implicit officeInfo: OfficeInfo[O], contract: AggregateContract.Aux[O, C, E, R]): AggregateOfficeRef[O, C, E, R]
  }

  def office[O] = new Office[O] {
    def apply[C <: Command, E <: DomainEvent, R : ClassTag]()(implicit officeInfo: OfficeInfo[O], contract: AggregateContract.Aux[O, C, E, R]): AggregateOfficeRef[O, C, E, R] = {
      val officeName = officeInfo.name
      AggregateOfficeRef[O](officeClientMap.getOrElseUpdate(officeName, system.actorOf(Props(new OfficeClient(officeName)))))
    }
  }

  class OfficeClient(officeName: String) extends Actor {
    override def receive: Receive = {
      case msg => clusterClient forward ClusterClient.Send(s"/system/sharding/$officeName", msg, localAffinity = true)
    }
  }

  private def system = context.system
}
