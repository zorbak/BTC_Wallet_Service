package shopping.cart

import scala.concurrent.duration._
import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.SupervisorStrategy
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.cluster.sharding.typed.scaladsl.Entity
import akka.cluster.sharding.typed.scaladsl.EntityContext
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.Effect
import akka.persistence.typed.scaladsl.EventSourcedBehavior
import akka.persistence.typed.scaladsl.ReplyEffect
import akka.persistence.typed.scaladsl.RetentionCriteria
import shopping.cart.CborSerializable

object Wallet {

  final case class State(items: Map[String, Double])
      extends CborSerializable {


    def isEmpty: Boolean =
      items.isEmpty

    def updateTransaction(datetime: String, amount: Double): State = {
      amount match {
        case 0 => copy(items = items - datetime)
        case _ => copy(items = items + (datetime -> amount))
      }
    }

    def toSummary: Summary =
      Summary(items)
  }
  object State {
    val empty =
      State(items = Map.empty)
  }

  sealed trait Command extends CborSerializable


  final case class addTransaction(
                            datetime: String,
                            amount: Double,
                            replyTo: ActorRef[StatusReply[Summary]])
      extends Command

  final case class AdjustTransaction(
                                       datetime: String,
                                       amount: Double,
                                       replyTo: ActorRef[StatusReply[Summary]])
    extends Command

  /**
   * Summary of the shopping cart state, used in reply messages.
   */
  final case class Summary(items: Map[String, Double])
      extends CborSerializable

  /**
   * This interface defines all the events that the ShoppingCart supports.
   */
  sealed trait Event extends CborSerializable {
    def cartId: String
  }

  final case class transactionAdded(cartId: String, datetime: String, amount: Double)
      extends Event


  final case class updateTransaction(
                                         cartId: String,
                                         datetime: String,
                                         newAmount: Double,
                                         oldAmount: Double)
      extends Event

  val EntityKey: EntityTypeKey[Command] =
    EntityTypeKey[Command]("Wallet")


  val tags = Vector.tabulate(5)(i => s"wallet-$i")


  def init(system: ActorSystem[_]): Unit = {
    val behaviorFactory: EntityContext[Command] => Behavior[Command] = {
      entityContext =>
        val i = math.abs(entityContext.entityId.hashCode % tags.size)
        val selectedTag = tags(i)
        Wallet(entityContext.entityId, selectedTag)
    }
    ClusterSharding(system).init(Entity(EntityKey)(behaviorFactory))
  }


  def apply(cartId: String, projectionTag: String): Behavior[Command] = {
    EventSourcedBehavior
      .withEnforcedReplies[Command, Event, State](
        persistenceId = PersistenceId(EntityKey.name, cartId),
        emptyState = State.empty,
        commandHandler =
          (state, command) => handleCommand(cartId, state, command),
        eventHandler = (state, event) => handleEvent(state, event))
      .withTagger(_ => Set(projectionTag))
      .withRetention(RetentionCriteria
        .snapshotEvery(numberOfEvents = 100, keepNSnapshots = 3))
      .onPersistFailure(
        SupervisorStrategy.restartWithBackoff(200.millis, 5.seconds, 0.1))
  }


  private def handleCommand(
                             walletId: String,
                             state: State,
                             command: Command): ReplyEffect[Event, State] = {

    wallet(walletId, state, command)
  }

  private def wallet(
      cartId: String,
      state: State,
      command: Command): ReplyEffect[Event, State] = {
    command match {
      case addTransaction(datetime, amount, replyTo) =>
        if (amount <= 0)
          Effect.reply(replyTo)(
            StatusReply.Error("Quantity must be greater than zero"))
        else
          Effect
            .persist(transactionAdded(cartId, datetime, amount))
            .thenReply(replyTo) { updatedCart =>
              StatusReply.Success(updatedCart.toSummary)
            }


    }
  }






  private def handleEvent(state: State, event: Event): State = {
    event match {
      case transactionAdded(_, datetime, amount) =>
        state.updateTransaction(datetime, amount)

    }
  }
  
}
