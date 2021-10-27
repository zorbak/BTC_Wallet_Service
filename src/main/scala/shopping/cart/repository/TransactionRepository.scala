package shopping.cart.repository

import akka.grpc.GrpcServiceException
import io.grpc.Status
import org.joda.time.{DateTime, DateTimeZone, Period}
import scalikejdbc._

import scala.collection.immutable.ListMap


trait TransactionRepository {
  def store(session: ScalikeJdbcSession, itemId: String, delta: Double): Unit
  def getTransaction(session: ScalikeJdbcSession, starttime: String, endtime: String): Map[String, Double]
}

class TransactionRepositoryImpl() extends TransactionRepository {

  override def store(
                       session: ScalikeJdbcSession,
                       datetime: String,
                       delta: Double): Unit = {
    session.db.withinTx { implicit dbSession =>

      val startDate = new DateTime(datetime).withZone(DateTimeZone.UTC).toString()
      sql"""
           INSERT INTO transaction_storage (datetime, amount) VALUES ($startDate, $delta)
         """.executeUpdate().apply()
    }
  }

  override def getTransaction(
                        session: ScalikeJdbcSession,
                        starttime: String, endtime: String): Map[String, Double] = {


    if (session.db.isTxAlreadyStarted) {
      session.db.withinTx { implicit dbSession =>

        val startDate = new DateTime(starttime).withZone(DateTimeZone.UTC)

        val endDate = new DateTime(endtime).withZone(DateTimeZone.UTC)

        val duration = new Period(startDate, endDate)

        val hoursDiff = duration.getHours

        val hoursDiffList = List.range(0,hoursDiff+1, 1)

        val x = hoursDiffList.map(y => startDate.plusHours(y))
        val output = try {
          x.map(datetime => select(datetime.toString()).getOrElse(0.0))
        }
        catch{
          case e: scalikejdbc.ResultSetExtractorException => throw new GrpcServiceException(
            Status.NOT_FOUND.withDescription(s"Invalid DateTime"))
        }
        val outputItems = (x.map(_.toString()) zip output).toMap
        ListMap(outputItems.toSeq.sortBy(_._1):_*)
      }
    } else {
      session.db.readOnly { implicit dbSession =>

        val startDate = new DateTime(starttime).withZone(DateTimeZone.UTC)

        val endDate = new DateTime(endtime).withZone(DateTimeZone.UTC)

        val duration = new Period(startDate, endDate)

        val hoursDiff = duration.getHours

        val hoursDiffList = List.range(0,hoursDiff+1, 1)

        val x = hoursDiffList.map(y => startDate.plusHours(y))

        val output = try {
          x.map(datetime => select(datetime.toString()).getOrElse(0.0))
        }
        catch{
          case e: scalikejdbc.ResultSetExtractorException => throw new GrpcServiceException(
            Status.NOT_FOUND.withDescription(s"Invalid DateTime"))
        }

        val outputItems = (x.map(_.toString()) zip output).toMap
        ListMap(outputItems.toSeq.sortBy(_._1):_*)
      }
    }
  }

  private def select(starttime: String)(implicit dbSession: DBSession) = {

    sql"SELECT sum(amount) as result FROM transaction_storage WHERE datetime <= ${starttime}"
          .map(_.double("result"))
          .toOption()
          .apply()

  }

}

