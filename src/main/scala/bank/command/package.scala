package bank

import java.util.UUID

package object command {
  trait Command extends CborSerialization

  trait Event extends CborSerialization

  trait State

  type Amount = BigDecimal

  type TransactionId = UUID
}
