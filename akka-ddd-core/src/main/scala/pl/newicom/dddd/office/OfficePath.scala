package pl.newicom.dddd.office

import akka.actor.ActorPath

sealed trait OfficePath[O] extends Serializable {
  private [dddd] def value: ActorPath
}

object OfficePath {
  def apply[O](v: ActorPath) = new OfficePath[O] {
    override def value: ActorPath = v
  }
}
