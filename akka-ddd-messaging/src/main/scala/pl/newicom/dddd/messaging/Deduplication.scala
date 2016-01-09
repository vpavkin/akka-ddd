package pl.newicom.dddd.messaging

import akka.contrib.pattern.ReceivePipeline
import akka.contrib.pattern.ReceivePipeline.{Inner, HandledCompletely}

import scala.collection.mutable
import scala.reflect.ClassTag

trait Deduplication[M <: Message, R] {
  this: ReceivePipeline =>
  implicit def M: ClassTag[M]

  private val processedMessages: mutable.Map[String, R] = mutable.Map.empty

  pipelineInner {
    case m: M =>
      processedMessages.get(m.id).map { result =>
        handleDuplicated(m, result)
        HandledCompletely
      }.getOrElse {
        Inner(m)
      }
  }

  def handleDuplicated(m: M, result: R)

  def messageProcessed(messageId: String, result: R): Unit = {
    processedMessages += (messageId -> result)
  }
}