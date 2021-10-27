
package shopping.cart

import akka.actor.typed.ActorSystem
import akka.projection.eventsourced.EventEnvelope
import akka.projection.jdbc.scaladsl.JdbcHandler
import org.slf4j.LoggerFactory
import org.joda.time.{DateTime, DateTimeZone, Period}
import shopping.cart.repository.{ScalikeJdbcSession, TransactionRepository}
class TransactionProjectHandler(
    tag: String,
    system: ActorSystem[_],
    repo: TransactionRepository)
    extends JdbcHandler[
      EventEnvelope[Wallet.Event],
      ScalikeJdbcSession]() { 

  private val log = LoggerFactory.getLogger(getClass)

  override def process(
      session: ScalikeJdbcSession,
      envelope: EventEnvelope[Wallet.Event]): Unit = {
    envelope.event match { 
      case Wallet.transactionAdded(_, datetime, amount) =>
        repo.store(session, datetime, amount)
        logItemCount(session, datetime, amount)
    }
  }

  private def logItemCount(
      session: ScalikeJdbcSession,
      starttime: String, amount: Double): Unit = {
    log.info(
      s"Add Transaction at {$starttime} with ${amount} BTC",
      tag,
      starttime)
  }

}

