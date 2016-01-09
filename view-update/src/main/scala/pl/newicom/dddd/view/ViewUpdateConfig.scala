package pl.newicom.dddd.view

import pl.newicom.dddd.office.OfficeInfo

abstract class ViewUpdateConfig[O] {
  def viewName: String
  def officeInfo: OfficeInfo[O]
}