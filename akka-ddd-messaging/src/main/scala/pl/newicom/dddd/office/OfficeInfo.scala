package pl.newicom.dddd.office

import pl.newicom.dddd.aggregate.EntityId

trait OfficeInfo[O] {
  def name: String
  def isSagaOffice: Boolean = false
  def clerkGlobalId(id: EntityId): String = s"$name-$id"
}

object OfficeInfo {
  def apply[O](implicit instance: OfficeInfo[O]): OfficeInfo[O] = instance
}