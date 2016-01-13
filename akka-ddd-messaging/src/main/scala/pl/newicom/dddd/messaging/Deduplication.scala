package pl.newicom.dddd.messaging

import akka.contrib.pattern.ReceivePipeline
import akka.contrib.pattern.ReceivePipeline.{HandledCompletely, Inner}

import scala.collection.mutable

trait Deduplication[R] {
  this: ReceivePipeline =>

  private val processedMessages: mutable.Map[String, R] = mutable.Map.empty

  pipelineInner {
    case m : Message =>
      processedMessages.get(m.id).map { result =>
        handleDuplicated(m, result)
        HandledCompletely
      }.getOrElse {
        Inner(m)
      }
  }

  def handleDuplicated(m: Message, result: R)

  def messageProcessed(messageId: String, result: R): Unit = {
    processedMessages += (messageId -> result)
  }
}