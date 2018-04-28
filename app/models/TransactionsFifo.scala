package models

import daos.TransactionDao.Transaction
import models.TransactionsFifo.{BuyerTransaction, SellerTransaction}

import scala.collection.mutable.{HashMap, ListBuffer, Map}

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

  private val buyerTxs: Map[String, ListBuffer[BuyerTransaction]] = HashMap()
  private val sellerTxs: Map[String, ListBuffer[SellerTransaction]] = HashMap()

  def add(tx: Transaction): Unit = {
    tx.txRole match {
      case "BUYER"  => addBuyerTx(tx)
      case "SELLER" =>
      case _ => throw new IllegalArgumentException(s"Unknown role value. Acceptable: 'BUYER', 'SELLER'. TxId: ${tx.id}.")
    }

    def addBuyerTx(tx: Transaction) = {
      buyerTxs.get(tx.cryptoSymbol) match {
        case Some(txs) => if(!txs.last.addTransaction(tx)) txs += toBuyerTransaction(tx)
        case None      => buyerTxs.put(tx.cryptoSymbol, ListBuffer(toBuyerTransaction(tx)))
      }
    }

    def toBuyerTransaction(tx: Transaction): BuyerTransaction = {
      BuyerTransaction(tx.exchangeRate, tx.cryptoAmount, tx.txValue)
    }
  }

  def printBuyersForTest: String = {

    def print(txs: ListBuffer[BuyerTransaction]): String = {
      txs.map(bTx => s"BuyerTransaction: ${bTx.exchangeRate}, ${bTx.amount}, ${bTx.value}").mkString("\n")
    }

    buyerTxs.map{case (crypto, txs) => s"${crypto} => \n${print(txs)}\n"}.mkString("\n")
  }

}
