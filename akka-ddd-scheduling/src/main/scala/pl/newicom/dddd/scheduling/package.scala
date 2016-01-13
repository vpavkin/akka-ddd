package pl.newicom.dddd

import pl.newicom.dddd.messaging.event.ClerkEventStream

package object scheduling {
  def currentDeadlinesStream(businessUnit: String) = ClerkEventStream("currentDeadlines", businessUnit)
}
