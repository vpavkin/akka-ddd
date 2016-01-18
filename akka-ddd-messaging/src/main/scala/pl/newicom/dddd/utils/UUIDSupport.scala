package pl.newicom.dddd.utils

import java.util.UUID

object UUIDSupport {
  def uuid: String = uuidObj.toString.replaceAllLiterally("-", "")
  def uuid7: String = uuid(7)
  def uuid10: String = uuid(10)
  def uuid(length: Int): String = uuid.take(length)
  def uuidObj: UUID = UUID.randomUUID()
}

trait UUIDSupport {
  def uuid = UUIDSupport.uuid
  def uuid7 = UUIDSupport.uuid7
  def uuid10 = UUIDSupport.uuid10
  def uuid(length: Int): String = UUIDSupport.uuid(length)
  def uuidObj = UUIDSupport.uuidObj
}
