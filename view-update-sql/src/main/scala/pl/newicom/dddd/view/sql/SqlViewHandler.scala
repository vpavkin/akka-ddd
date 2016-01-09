package pl.newicom.dddd.view.sql

import com.typesafe.config.Config
import pl.newicom.dddd.aggregate.DomainEvent
import pl.newicom.dddd.messaging.event.DomainEventMessage
import pl.newicom.dddd.view.ViewHandler
import slick.dbio.DBIOAction.sequence
import slick.driver.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}

class SqlViewHandler[-E <: DomainEvent, O](override val config: Config, override val vuConfig: SqlViewUpdateConfig[E, O])
                    (implicit val profile: JdbcProfile, ex: ExecutionContext)
  extends ViewHandler[E, O](vuConfig) with SqlViewStoreConfiguration with FutureHelpers {

  private lazy val viewMetadataDao = new ViewMetadataDao

  def handle(eventMessage: DomainEventMessage[E], eventNumber: Long): Future[Unit] =
    viewStore.run {
      sequence(vuConfig.projections.map(_.consume(eventMessage))) >>
      viewMetadataDao.insertOrUpdate(viewName, eventNumber)
    }.mapToUnit

  def lastEventNumber: Future[Option[Long]] =
    viewStore.run {
      viewMetadataDao.lastEventNr(viewName)
    }

}
