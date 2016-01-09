package pl.newicom.dddd

import pl.newicom.dddd.aggregate.AggregateRootBehavior
import pl.newicom.dddd.messaging.event.ClerkEventStream
import pl.newicom.dddd.office.{OfficeContract, OfficeInfo}

package object scheduling {

  def currentDeadlinesStream(businessUnit: String) = ClerkEventStream("currentDeadlines", businessUnit)
}
