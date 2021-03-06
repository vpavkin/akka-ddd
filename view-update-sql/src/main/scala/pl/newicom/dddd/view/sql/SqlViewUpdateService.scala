package pl.newicom.dddd.view.sql

import akka.NotUsed
import eventstore.EsConnection
import pl.newicom.dddd.aggregate.DomainEvent
import pl.newicom.dddd.messaging.event.DomainEventMessageStream
import pl.newicom.dddd.view.ViewUpdateService.ViewUpdateInitiated
import pl.newicom.dddd.view.{ViewHandler, ViewUpdateService}
import slick.dbio.DBIO
import slick.dbio.DBIOAction.successful
import slick.driver.JdbcProfile

import scala.concurrent.Future

abstract class SqlViewUpdateService[E <: DomainEvent, O](eventSource: DomainEventMessageStream[E, NotUsed])(implicit val profile: JdbcProfile) extends ViewUpdateService[E, O](eventSource) with FutureHelpers {
  this: SqlViewStoreConfiguration =>

  type VUConfig = SqlViewUpdateConfig[E, O]

  override def ensureViewStoreAvailable: Future[Unit] = {
    viewStore.run(profile.defaultTables).mapToUnit
  }

  override def onViewUpdateInit(esCon: EsConnection): Future[ViewUpdateInitiated] =
    viewStore.run {
      onViewUpdateInit >> successful(ViewUpdateInitiated(esCon))
    }

  def onViewUpdateInit: DBIO[Unit] =
    new ViewMetadataDao().ensureSchemaCreated



  override def viewHandler(vuConfig: SqlViewUpdateConfig[E, O]): ViewHandler[E, O] =
    new SqlViewHandler(config, vuConfig)
}
