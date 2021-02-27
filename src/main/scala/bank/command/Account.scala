package bank.command

import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect, RetentionCriteria}

import scala.concurrent.duration._

case class Account(balance: Amount) extends State {

  // Commands
  final case class OpenAccount(initialBalance: Amount, replyTo: ActorRef[Response]) extends Command

  final case class CreditAccount(amount: Amount, transactionId: TransactionId, replyTo: ActorRef[Response])
    extends Command

  final case class DebitAccount(amount: Amount, transactionId: TransactionId, replyTo: ActorRef[Response])
    extends Command

  //Events
  final case class AccountOpened(initialBalance: Amount) extends Event

  final case class AccountCredited(amount: Amount, transactionId: TransactionId) extends Event

  final case class AccountDebited(amount: Amount, transactionId: TransactionId) extends Event

  // Response
  trait Response extends CborSerialization

  final case class AccountResponse(message: String) extends Response

  def apply(): Behavior[Command] = {
    EventSourcedBehavior[Command, Event, Account](
      persistenceId = PersistenceId("MoneyTransfer", this.toString),
      emptyState = Account.empty,
      commandHandler = (state, command) => handleCommand(state, command),
      eventHandler = (state, event) => handleEvent(state, event))
      .withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 100, keepNSnapshots = 3))
      .onPersistFailure(SupervisorStrategy.restartWithBackoff(200.millis, 5.seconds, 0.1))
  }

  private def handleCommand(state: Account, command: Command): ReplyEffect[Event, Account] = {
    command match {
      case OpenAccount(initialBalance, replyTo) =>
        Effect
          .persist(AccountOpened(initialBalance))
          .thenReply(replyTo) { newAccount =>
            AccountResponse(s"Account opened successfully $newAccount")
          }
      case DebitAccount(amount, transactionId, replyTo) =>
        Effect
          .persist(AccountDebited(amount, transactionId))
          .thenReply(replyTo) { updatedAccount =>
            AccountResponse(s"$amount was debited. Current balance is ${updatedAccount.balance}")
          }
      case CreditAccount(amount, transactionId, replyTo) =>
        Effect
          .persist(AccountCredited(amount, transactionId))
          .thenReply(replyTo) { updatedAccount =>
            AccountResponse(s"$amount was credited. Current balance is ${updatedAccount.balance}")
          }
    }
  }

  private def handleEvent(state: State, event: Event): Account = {
    event match {
      case AccountOpened(initialBalance) => copy(balance = initialBalance)
      case AccountDebited(amount, _) => copy(balance = balance - amount)
      case AccountCredited(amount, _) => copy(balance = balance + amount)
    }
  }
}

object Account {
  val empty: Account = Account(0.0)
}