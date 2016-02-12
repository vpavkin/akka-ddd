package pl.newicom.eventstore.json

import java.nio.charset.Charset

import akka.actor._
import akka.persistence.eventstore.snapshot.EventStoreSnapshotStore.SnapshotEvent.Snapshot
import akka.persistence.{PersistentRepr, SnapshotMetadata}
import akka.serialization.{Serialization, SerializationExtension}
import org.json4s.Extraction.decompose
import org.json4s.JsonAST.{JField, JObject, JString}
import org.json4s.native.Serialization.{read, write}
import org.json4s.reflect.TypeInfo
import org.json4s.{Formats, FullTypeHints, _}
import pl.newicom.dddd.aggregate.Command
import pl.newicom.dddd.delivery.protocol.{Delivered, Processed}
import pl.newicom.dddd.messaging.MetaData
import pl.newicom.dddd.scheduling.{CommandScheduled, ScheduledCommandMetadata}
import pl.newicom.dddd.serialization.JsonSerHints._
import pl.newicom.dddd.serialization.{JsonExtraSerHints, JsonSerHints}

import scala.reflect.ClassTag

/**
  * The reason for using Extension mechanism is that
  * pl.newicom.eventstore.json.JsonSerializerExtensionImpl.ActorRefSerializer
  * requires access to ExtendedActorSystem
  *
  * @param system ExtendedActorSystem (injected automatically by Akka)
  */
class JsonSerializerExtensionImpl(system: ExtendedActorSystem) extends Extension {

  val extraHints = JsonExtraSerHints(
    typeHints =
      new FullTypeHints(
        List(classOf[MetaData], classOf[Processed], classOf[Delivered], classOf[PersistentRepr], classOf[CommandScheduled])
      ),
    serializers =
      List(ActorRefSerializer, ActorPathSerializer, new ScheduledEventSerializer, new SnapshotJsonSerializer(system))
  )

  val UTF8 = Charset.forName("UTF-8")

  def fromBinary[A](bytes: Array[Byte], hints: JsonSerHints)(implicit ct: ClassTag[A]): A = {
    implicit val formats: Formats = hints ++ extraHints
    implicit val manifest: Manifest[A] = Manifest.classType(ct.runtimeClass)
    try {
      read(new String(bytes, UTF8))
    } catch {
      case th: Throwable =>
        th.printStackTrace()
        throw th;
    }
  }

  def toBinary(o: AnyRef, hints: JsonSerHints) = {
    implicit val formats: Formats = hints ++ extraHints
    write(o).getBytes(UTF8)
  }

  object ActorRefSerializer extends CustomSerializer[ActorRef](format => (
    {
      case JString(s) => system.provider.resolveActorRef(s)
      case JNull => null
    },
    {
      case x: ActorRef => JString(Serialization.serializedActorPath(x))
    }
    ))


  object ActorPathSerializer extends CustomSerializer[ActorPath](format => (
    {
      case JString(string) =>
        val systemName = system.name
        val path = ActorPath.fromString(string)
        val updatedString = path.toSerializationFormatWithAddress(Address("akka", systemName))
        ActorPath.fromString(updatedString)
    },
    { case x: ActorPath => JString(x.toSerializationFormat) }
    ))

}

object JsonSerializerExtension extends ExtensionId[JsonSerializerExtensionImpl] with ExtensionIdProvider {
  override def createExtension(system: ExtendedActorSystem) = new JsonSerializerExtensionImpl(system)
  override def lookup(): ExtensionId[_ <: Extension] = JsonSerializerExtension
  override def get(system: ActorSystem) = super.get(system)

}


class ScheduledEventSerializer extends Serializer[CommandScheduled] {
  val Clazz = classOf[CommandScheduled]

  def deserialize(implicit formats: Formats) = {
    case (TypeInfo(Clazz, _), JObject(List(
    JField("businessUnit", JString(businessUnit)),
    JField("metadata", metadata),
    JField("commandClass", JString(commandClassName)),
    JField("command", command)))) =>
      val eventClass = Class.forName(commandClassName)
      val eventObj = command.extract[Command](formats, Manifest.classType(eventClass))
      val metadataObj = metadata.extract[ScheduledCommandMetadata]
      CommandScheduled(businessUnit, metadataObj, eventObj)
  }

  def serialize(implicit formats: Formats) = {
    case CommandScheduled(businessUnit, metadata, event) =>
      JObject(
        "businessUnit" -> JString(businessUnit),
        "jsonClass"   -> JString(classOf[CommandScheduled].getName),
        "metadata"    -> decompose(metadata),
        "commandClass"  -> JString(event.getClass.getName),
        "command"       -> decompose(event)
      )
  }
}

case class SnapshotJsonSerializer(sys: ActorSystem) extends Serializer[Snapshot] {
  val Clazz = classOf[Snapshot]

  import akka.serialization.{Serialization => SysSerialization}
  lazy val serialization: SysSerialization = SerializationExtension(sys)

  def deserialize(implicit format: Formats) = {
    case (TypeInfo(Clazz, _), JObject(List(
    JField("dataClass", JString(dataClass)),
    JField("data", JString(x)),
    JField("metadata", metadata)))) =>
      import Base64._
      val data = serialization.deserialize(x.toByteArray, Class.forName(dataClass)).get
      val metaData = metadata.extract[SnapshotMetadata]
      Snapshot(data, metaData)
  }

  def serializeAnyRef(data: AnyRef)(implicit format: Formats): String = {
    import Base64._
    serialization.serialize(data).get.toBase64
  }

  def serialize(implicit format: Formats) = {
    case Snapshot(data, metadata) =>
      val dataSerialized: String = data match {
        case data: AnyRef => serializeAnyRef(data)
        case _ => data.toString
      }
      JObject(
        "jsonClass" -> JString(Clazz.getName),
        "dataClass" -> JString(data.getClass.getName),
        "data"      -> JString(dataSerialized),
        "metadata"  -> decompose(metadata)
      )
  }
}