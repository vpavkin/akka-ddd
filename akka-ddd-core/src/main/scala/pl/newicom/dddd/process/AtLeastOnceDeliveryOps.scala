package pl.newicom.dddd.process

import akka.actor.ActorPath
import akka.persistence.AtLeastOnceDelivery
import pl.newicom.dddd.aggregate.Command
import pl.newicom.dddd.messaging.Message
import pl.newicom.dddd.messaging.command.CommandMessage
import pl.newicom.dddd.office.{OfficeContract, OfficePath}

trait AtLeastOnceDeliveryOps { self: AtLeastOnceDelivery =>
  def eventMessage: Message

  def deliverMsg(office: ActorPath, msg: Message): Unit = deliver(office)(id => msg.withDeliveryId(id))

  implicit class AtLeastOnceDeliveryOps[O, Cmd <: Command](officePath: OfficePath[O])(implicit officeContract: OfficeContract[O] { type CommandImpl = Cmd }) {
    def !!(command: Cmd): Unit =
      deliverMsg(officePath.value, CommandMessage(command).causedBy(eventMessage))
  }
}
