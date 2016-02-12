import sbt._
import Keys._
import xerial.sbt.Sonatype.sonatypeSettings

object Publish {
  val rwSnapshots = "Read/Write QIWI Snapshots Repo" at NexusSettings.readWriteUrlFor("snapshots")
  val rwReleases = "Read/Write QIWI Releases Repo" at  NexusSettings.readWriteUrlFor("releases")
  def qiwiNexusSettings = Seq(
    publishTo := Some {
      if (isSnapshot.value) rwSnapshots else rwReleases
    },
    credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")
  )
  lazy val settings = qiwiNexusSettings :+ (pomExtra :=
    <scm>
      <url>git@github.com:notxcain/akka-ddd.git</url>
      <connection>scm:git:git@github.com:notxcain/akka-ddd.git</connection>
      <developerConnection>scm:git:git@github.com:notxcain/akka-ddd.git</developerConnection>
    </scm>
      <developers>
        <developer>
          <id>newicom</id>
          <name>Pawel Kaczor</name>
          <url>http://pkaczor.blogspot.com</url>
        </developer>
        <developer>
          <id>qiwi</id>
          <name>Denis Mikhaylov</name>
        </developer>
      </developers>)
}

object NexusSettings {
  val realm = "Sonatype Nexus Repository Manager"
  val host = "maven.osmp.ru"
  def readWriteUrlFor(content: String) = s"https://$host/${readWritePathTo(content)}"
  def readOnlyURLFor(content: String) = s"https://$host/${readOnlyPathTo(content)}"
  def readWritePathTo(content: String) = s"nexus/content/repositories/qiwiweb-$content/"
  def readOnlyPathTo(content: String) = s"nexus/content/repositories/$content/"
}