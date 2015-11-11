package pl.newicom.dddd.process.typesafe

import akka.actor.ActorPath

trait OfficePath[O] {
  def value: ActorPath
}

object OfficePath {
  def apply[O](v: ActorPath) = new OfficePath[O] {
    override def value: ActorPath = v
  }
}
