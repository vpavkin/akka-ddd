package pl.newicom.dddd.view.sql

import pl.newicom.dddd.aggregate.DomainEvent
import pl.newicom.dddd.office.OfficeInfo
import pl.newicom.dddd.view.ViewUpdateConfig

case class SqlViewUpdateConfig[-E <: DomainEvent, O](
                                override val viewName: String,
                                override val officeInfo: OfficeInfo[O],
                                projections: List[Projection[E]])
  extends ViewUpdateConfig[O]