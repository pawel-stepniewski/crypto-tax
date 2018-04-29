package models

import daos.TransactionDao.{Transaction, TransactionId}
import models.TransactionsFifo.{BuyerTransaction, SellerTransaction, SellerTransactionRow}

import scala.annotation.tailrec
import scala.collection.mutable.{HashMap, Map}

object TransactionsFifo {

  case class SellerTransactionRow(id: TransactionId, cryptoSymbol: String, income: BigInt, cost: BigInt) {
    def incomeMinusCost: BigInt = income - cost

    def printForTests: String = s"SellerTransactionRow(id ${id}, cryptoSymbol ${cryptoSymbol}, income ${income}, cost ${cost})"
  }

  object SellerTransactionRow {
    def apply(cryptoSymbol: String, sTx: SellerTransaction): SellerTransactionRow = new SellerTransactionRow(sTx.id, cryptoSymbol, sTx.value, sTx.costValue)
  }

  private case class SellerTransaction(id: TransactionId, exchangeRate: BigInt, amount: BigInt, value: BigInt, costAmount: BigInt, costValue: BigInt) {

    def amountToCost = amount - costAmount

    def includeAllCost: Boolean = amount == costAmount

    def addCost(bTx: BuyerTransaction): SellerTransaction = {
      def limitCostValue(cost: BigInt): BigInt = {
        if(cost > value) value else cost
      }

      (amountToCost - bTx.amount) match {
        case x if x <= 0 => SellerTransaction(id, exchangeRate, amount, value, amount, limitCostValue(amount*bTx.exchangeRate))
        case x if x > 0  => SellerTransaction(id, exchangeRate, amount, value, costAmount + bTx.amount, costValue + limitCostValue(amount*bTx.exchangeRate))
      }
    }
  }

  private case class BuyerTransaction(exchangeRate: BigInt, amount: BigInt, value: BigInt) {

    def merge(tx: Transaction): Option[BuyerTransaction] = {
      (exchangeRate == tx.exchangeRate) match {
        case true  => Some(BuyerTransaction(exchangeRate, amount + tx.cryptoAmount, value + tx.txValue))
        case false => None
      }
    }

    def refund(sTx: SellerTransaction): Option[BuyerTransaction] = {
      if (amount - sTx.amountToCost <= 0) None else Some(BuyerTransaction(exchangeRate, amount - sTx.amountToCost, exchangeRate*(amount - sTx.amountToCost)))
    }
  }
}


class TransactionsFifo {

  private val log = play.api.Logger(this.getClass)

  private val buyerTxs: Map[String, List[BuyerTransaction]] = HashMap()

  private val sellerTxs: Map[String, List[SellerTransaction]] = HashMap()


  def sellerTransactions: List[SellerTransactionRow] = sellerTxs.map(t => t._2.map(SellerTransactionRow(t._1, _))).flatten.toList

  def add(tx: Transaction): Unit = {

    def addBuyerTx(tx: Transaction) = {
      buyerTxs.get(tx.cryptoSymbol) match {
        case Some(bTxs :+ last) => buyerTxs.put(tx.cryptoSymbol, last.merge(tx).map(bTxs :+ _).getOrElse(bTxs :+ last :+ toBuyerTx(tx)))
        case Some(Nil)       => buyerTxs.put(tx.cryptoSymbol, List(toBuyerTx(tx)))
        case None               => buyerTxs.put(tx.cryptoSymbol, List(toBuyerTx(tx)))
      }
    }

    def toBuyerTx(tx: Transaction): BuyerTransaction = {
      BuyerTransaction(tx.exchangeRate, tx.cryptoAmount, tx.txValue)
    }

    def addSellerTx(tx: Transaction) = {
      @tailrec
      def addCost(sTx: SellerTransaction, bTxs: List[BuyerTransaction]): (SellerTransaction, List[BuyerTransaction]) = {
        def refundBTxs(sTx: SellerTransaction, bTxs: List[BuyerTransaction]): List[BuyerTransaction] = {
          bTxs.head.refund(sTx) match {
            case Some(rBTx) => rBTx :: bTxs.tail
            case None       => bTxs.tail
          }
        }

        log.debug(s"Recursive call of addCost function. TxId: ${tx.id}, includeAllCosts ${sTx.includeAllCost}, amount ${sTx.amount}, costAmount ${sTx.costAmount}")
        if (bTxs.isEmpty) {
          log.warn(s"Transaction doesn't include all buy costs! Did you import all buyer transaction? TxId ${tx.id}.")
          (sTx, bTxs)
        }

        else {
          log.debug(s"Add cost to seller transaction. TxId ${tx.id}, sTxAmountToCost ${sTx.amountToCost}, bTxAmount ${bTxs.head.amount}")
          sTx.addCost(bTxs.head) match {
            case cSTx if cSTx.includeAllCost => (cSTx, refundBTxs(sTx, bTxs))
            case cSTx                        => addCost(cSTx, refundBTxs(sTx, bTxs))
          }
        }
      }

      val bTxs = buyerTxs.getOrElse(tx.cryptoSymbol, List())
      val effects = addCost(toSellerTx(tx), bTxs)

      buyerTxs.put(tx.cryptoSymbol, effects._2)

      sellerTxs.get(tx.cryptoSymbol) match {
        case Some(sTxs) => sellerTxs.put(tx.cryptoSymbol, sTxs :+ effects._1)
        case None       => sellerTxs.put(tx.cryptoSymbol, List(effects._1))
      }
    }

    def toSellerTx(tx: Transaction): SellerTransaction = {
      SellerTransaction(tx.id, tx.exchangeRate, tx.cryptoAmount, tx.txValue, BigInt(0), BigInt(0))
    }

    log.debug(s"Processing transaction. TxId ${tx.id}, type ${tx.txRole}.")
    tx.txRole match {
      case "BUYER"  => addBuyerTx(tx)
      case "SELLER" => addSellerTx(tx)
      case _ => throw new IllegalArgumentException(s"Unknown role value. Acceptable: 'BUYER', 'SELLER'. TxId ${tx.id}.")
    }
  }
}
