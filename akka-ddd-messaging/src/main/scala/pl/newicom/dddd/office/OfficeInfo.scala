package pl.newicom.dddd.office

trait OfficeInfo[A] {
  def name: String
  def isSagaOffice: Boolean = false
}