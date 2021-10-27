package shopping.cart

import java.util.concurrent.TimeoutException
import scala.concurrent.{ExecutionContext, Future}
import akka.actor.typed.{ActorSystem, DispatcherSelector}
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.grpc.GrpcServiceException
import akka.util.Timeout
import io.grpc.Status
import org.slf4j.LoggerFactory
import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import org.joda.time.{DateTime, DateTimeZone, Period}
import scalikejdbc.UnexpectedNullValueException
import shopping.cart.Wallet
import shopping.cart.proto.Transaction
import shopping.cart.repository.{ScalikeJdbcSession, TransactionRepository}



class WalletServiceImpl(
                         system: ActorSystem[_],
                         transactionRepository: TransactionRepository)
    extends proto.WalletService {


  import system.executionContext

  private val logger = LoggerFactory.getLogger(getClass)

  implicit private val timeout: Timeout =
    Timeout.create(
      system.settings.config.getDuration("shopping-cart-service.ask-timeout"))

  private val sharding = ClusterSharding(system)


  private val blockingJdbcExecutor: ExecutionContext =
    system.dispatchers.lookup(
      DispatcherSelector
        .fromConfig("akka.projection.jdbc.blocking-jdbc-dispatcher")
    )


  override def addTransaction(in: proto.AddTransactionRequest): Future[proto.wallet] = {
    logger.info("addTransaction at {} to wallet {}", in.datetime, in.walletId)
    val entityRef = sharding.entityRefFor(Wallet.EntityKey, in.walletId)
    val reply: Future[Wallet.Summary] =
      entityRef.askWithStatus(Wallet.addTransaction(in.datetime, in.amount, _))
    val response = reply.map(wallet => toProtoWallet(wallet))
    convertError(response)
  }

  override def updateTransaction(in: proto.UpdateTransactionRequest): Future[proto.wallet] = {
    logger.info("updateTransaction at {} to wallet {}", in.datetime, in.walletId)
    val entityRef = sharding.entityRefFor(Wallet.EntityKey, in.walletId)

    def command(replyTo: ActorRef[StatusReply[Wallet.Summary]]) =
      Wallet.AdjustTransaction(in.datetime, in.amount, replyTo)

    val reply: Future[Wallet.Summary] =
      entityRef.askWithStatus(command(_))
    val response = reply.map(wallet => toProtoWallet(wallet))
    convertError(response)
  }



  private def toProtoWallet(cart: Wallet.Summary): proto.wallet = {
    proto.wallet(
      cart.items.iterator.map { case (itemId, quantity) =>
        proto.Transaction(itemId, quantity)
      }.toSeq)
  }

  private def toProtoWalletMap(cart: Map[String, Double]): Seq[Transaction]= {
    cart.map( x => proto.Transaction(x._1, x._2)).toSeq

  }

  private def convertError[T](response: Future[T]): Future[T] = {
    response.recoverWith {
      case _: TimeoutException =>
        Future.failed(
          new GrpcServiceException(
            Status.UNAVAILABLE.withDescription("Operation timed out")))
      case exc =>
        Future.failed(
          new GrpcServiceException(
            Status.INVALID_ARGUMENT.withDescription(exc.getMessage)))
    }
  }


  override def getWalletBalance(in: proto.GetWalletBalanceRequest)
      : Future[proto.GetWalletBalanceResponse] = {
    Future {
      ScalikeJdbcSession.withSession { session =>
        transactionRepository.getTransaction(session, in.starttime, in.endtime)
      }
    }(blockingJdbcExecutor).map(transaction =>
      if(transaction.nonEmpty) {
        val response = toProtoWalletMap(transaction)
        proto.GetWalletBalanceResponse(response)
      }
      else {
        throw new GrpcServiceException(
          Status.NOT_FOUND.withDescription(s"Invalid datetime"))
      }
    )
  }
}

