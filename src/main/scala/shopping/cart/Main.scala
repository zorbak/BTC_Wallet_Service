package shopping.cart

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.ActorSystem
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import org.slf4j.LoggerFactory

import scala.util.control.NonFatal
import org.joda.time.{DateTime, DateTimeZone, Period}
import shopping.cart.repository.{ScalikeJdbcSetup, TransactionRepositoryImpl}
import shopping.cart.{WalletServer, WalletServiceImpl}

object Main {

  val logger = LoggerFactory.getLogger("shopping.cart.Main")

  def main(args: Array[String]): Unit = {
    val system =
      ActorSystem[Nothing](Behaviors.empty, "WalletService")
    try {
      init(system)
    } catch {
      case NonFatal(e) =>
        logger.error("Terminating due to initialization failure.", e)
        system.terminate()
    }
  }

  def init(system: ActorSystem[_]): Unit = {
    
    ScalikeJdbcSetup.init(system) 

    AkkaManagement(system).start()
    ClusterBootstrap(system).start()
    Wallet.init(system)

    val transactionRepository = new TransactionRepositoryImpl()
    TransactionProjection.init(system, transactionRepository)

    val grpcInterface =
      system.settings.config.getString("shopping-cart-service.grpc.interface")
    val grpcPort =
      system.settings.config.getInt("shopping-cart-service.grpc.port")
    val grpcService =
      new WalletServiceImpl(system, transactionRepository)
    WalletServer.start(grpcInterface, grpcPort, system, grpcService)
  }

}
