
package shopping.cart

import akka.actor.typed.ActorSystem
import akka.cluster.sharding.typed.ShardedDaemonProcessSettings
import akka.cluster.sharding.typed.scaladsl.ShardedDaemonProcess
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.persistence.query.Offset
import akka.projection.eventsourced.EventEnvelope
import akka.projection.eventsourced.scaladsl.EventSourcedProvider
import akka.projection.jdbc.scaladsl.JdbcProjection
import akka.projection.scaladsl.{ExactlyOnceProjection, SourceProvider}
import akka.projection.{ProjectionBehavior, ProjectionId}
import org.joda.time.{DateTime, DateTimeZone, Period}
import shopping.cart.repository.{ScalikeJdbcSession, TransactionRepository}
object TransactionProjection {
  
  def init(
      system: ActorSystem[_],
      repository: TransactionRepository): Unit = {
    ShardedDaemonProcess(system).init( 
      name = "TransactionProjection",
      Wallet.tags.size,
      index =>
        ProjectionBehavior(createProjectionFor(system, repository, index)),
      ShardedDaemonProcessSettings(system),
      Some(ProjectionBehavior.Stop))
  }
  

  private def createProjectionFor(
                                   system: ActorSystem[_],
                                   repository: TransactionRepository,
                                   index: Int)
      : ExactlyOnceProjection[Offset, EventEnvelope[Wallet.Event]] = {
    val tag = Wallet.tags(index)

    val sourceProvider
        : SourceProvider[Offset, EventEnvelope[Wallet.Event]] =
      EventSourcedProvider.eventsByTag[Wallet.Event](
        system = system,
        readJournalPluginId = JdbcReadJournal.Identifier, 
        tag = tag)

    JdbcProjection.exactlyOnce( 
      projectionId = ProjectionId("TransactionProjection", tag),
      sourceProvider,
      handler = () =>
        new TransactionProjectHandler(tag, system, repository),
      sessionFactory = () => new ScalikeJdbcSession())(system)
  }

}

