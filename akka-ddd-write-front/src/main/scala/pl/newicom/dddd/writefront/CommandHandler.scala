package pl.newicom.dddd.writefront

import akka.actor.Actor



trait CommandHandler extends GlobalOfficeClientSupport {
  this: Actor =>
}