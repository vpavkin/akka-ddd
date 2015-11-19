package pl.newicom.dddd.delivery.protocol

/**
 * At-Most-Once Delivery protocol
 */
/***/
object Received                                        extends Receipt
case class Processed(result: Any = "OK") extends Receipt
