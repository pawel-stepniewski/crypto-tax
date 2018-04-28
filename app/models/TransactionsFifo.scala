package models

import daos.TransactionDao.Transaction
import models.TransactionsFifo.{BuyerTransaction, SellerTransaction}

import scala.collection.mutable.ListBuffer

object TransactionsFifo {

  case class SellerTransaction(id: String, income: BigInt, cost: BigInt)

  case class BuyerTransaction(exchangeRate: BigInt, var amount: BigInt, var value: BigInt) {

    def addTransaction(tx: Transaction): Boolean = {
      if(exchangeRate == tx.exchangeRate) {
        amount += tx.cryptoAmount
        value += tx.txValue
        true
      }

      else {
        false
      }
    }
  }
}


class TransactionsFifo {

  private val log = play.api.Logger(this.getClass)

  private var buyerTxs: ListBuffer[BuyerTransaction] = ListBuffer()
  private var sellerTxs: ListBuffer[SellerTransaction] = ListBuffer()

  def add(tx: Transaction): Unit = {
    tx.txRole match {
      case "BUYER"  => addBuyerTx(tx)
      case "SELLER" =>
      case _ => throw new IllegalArgumentException(s"Unknown role value. Acceptable: 'BUYER', 'SELLER'. TxId: ${tx.id}.")
    }

    def addBuyerTx(tx: Transaction) = {
      buyerTxs.isEmpty match {
        case true  => buyerTxs += toBuyerTransaction(tx)
        case false => if(!buyerTxs.last.addTransaction(tx)) buyerTxs += toBuyerTransaction(tx)
      }
    }

    def toBuyerTransaction(tx: Transaction): BuyerTransaction = {
      BuyerTransaction(tx.exchangeRate, tx.cryptoAmount, tx.txValue)
    }
  }

  def printBuyersForTest: String = {
    buyerTxs.map(bTx => s"BuyerTransaction: ${bTx.exchangeRate}, ${bTx.amount}, ${bTx.value}").mkString("\n")
  }

}
