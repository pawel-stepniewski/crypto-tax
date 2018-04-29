package models

import java.util.Date

import daos.TransactionDao.{Transaction, TransactionId}
import models.TransactionsFifo._

import scala.annotation.tailrec
import scala.collection.mutable.{HashMap, Map}
import scala.math.BigDecimal.RoundingMode

object TransactionsFifo {

  case class SellerTransactionRow(id: TransactionId,
                                  cryptoSymbol: String,
                                  value: String,
                                  cost: String,
                                  incomeMinusCost: String,
                                  txDateTime: Date) {

    def printForTests: String = s"SellerTransactionRow(id ${id}, cryptoSymbol ${cryptoSymbol}, income ${value}, cost ${cost}, date ${txDateTime})"
  }

  object SellerTransactionRow {
    def apply(cryptoSymbol: String, sTx: SellerTransaction): SellerTransactionRow =
      new SellerTransactionRow(sTx.id, cryptoSymbol, sTx.value.toString, sTx.costValue.toString, (sTx.value - sTx.costValue).toString, sTx.txDateTime)
  }


  private case class NewTransaction(id: TransactionId,
                            cryptoSymbol: String,
                            cryptoAmount: CryptoAmount,
                            txValue: CryptoValue,
                            exchangeRate: CryptoValue,
                            txRole: String,
                            commission: Option[CryptoValue],
                            transactionDateTime: Date)

  private object NewTransaction {

    def apply(tx: Transaction): NewTransaction = new NewTransaction(tx.id,
      tx.cryptoSymbol,
      CryptoAmount(tx.cryptoAmount),
      CryptoValue(tx.txValue),
      CryptoValue(tx.exchangeRate),
      tx.txRole,
      tx.commission.map(CryptoValue(_)),
      tx.transactionDateTime)
  }


  private case class SellerTransaction(id: TransactionId,
                                       exchangeRate: CryptoValue,
                                       amount: CryptoAmount,
                                       value: CryptoValue,
                                       costAmount: CryptoAmount,
                                       costValue: CryptoValue,
                                       txDateTime: Date) {

    def amountToCost: CryptoAmount = amount - costAmount

    def includeAllCost: Boolean = amount == costAmount

    def addCost(bTx: BuyerTransaction): SellerTransaction = {
      def limitCostValue(cost: CryptoValue): CryptoValue = {
        if(cost > value) value else cost
      }

      (amountToCost - bTx.amount) match {
        case x if x <= 0 => SellerTransaction(id, exchangeRate, amount, value, amount, limitCostValue(bTx.exchangeRate * amount), txDateTime)
        case x           => SellerTransaction(id, exchangeRate, amount, value, costAmount + bTx.amount, costValue + limitCostValue(bTx.exchangeRate * amount), txDateTime)
      }
    }
  }


  private case class BuyerTransaction(exchangeRate: CryptoValue, amount: CryptoAmount, value: CryptoValue) {

    def merge(tx: NewTransaction): Option[BuyerTransaction] = {
      (exchangeRate == tx.exchangeRate) match {
        case true  => Some(BuyerTransaction(exchangeRate, amount + tx.cryptoAmount, value + tx.txValue))
        case false => None
      }
    }

    def refund(sTx: SellerTransaction): Option[BuyerTransaction] = {
      if (amount - sTx.amountToCost <= 0) None else Some(BuyerTransaction(exchangeRate, amount - sTx.amountToCost, exchangeRate*(amount - sTx.amountToCost)))
    }
  }


  private sealed trait CryptoNumber

  private final case class CryptoValue(value: BigDecimal) extends CryptoNumber {

    def *(amount: CryptoAmount): CryptoValue = CryptoValue((value * amount.amount).setScale(2, RoundingMode.HALF_UP))

    def >(otherValue: CryptoValue): Boolean = value > otherValue.value

    def +(otherAmount: CryptoValue): CryptoValue = CryptoValue(value + otherAmount.value)

    def -(otherAmount: CryptoValue): CryptoValue = CryptoValue(value - otherAmount.value)

    override def toString: String = value.toString()

  }

  private object CryptoValue {

    def apply(value: BigInt): CryptoValue = {
      val vs = value.toString()
      new CryptoValue(BigDecimal(s"${vs.take(vs.length - 2)}.${vs.takeRight(2)}").setScale(2, RoundingMode.HALF_UP))
    }
  }


  private final case class CryptoAmount(amount: BigDecimal) extends CryptoNumber {

    def ==(otherAmount: CryptoAmount): Boolean = amount == otherAmount.amount

    def -(otherAmount: CryptoAmount): CryptoAmount = CryptoAmount(amount - otherAmount.amount)

    def +(otherAmount: CryptoAmount): CryptoAmount = CryptoAmount(amount + otherAmount.amount)

    def <=(bigDecimal: BigDecimal): Boolean = amount <= bigDecimal

    override def toString: String = amount.toString()
  }

  private object CryptoAmount {

    def apply(value: BigInt): CryptoAmount = {
      val vs = value.toString()
      new CryptoAmount(BigDecimal(s"${vs.take(vs.length - 8)}.${vs.takeRight(8)}").setScale(2, RoundingMode.HALF_UP))
    }
  }
}


class TransactionsFifo {

  private val log = play.api.Logger(this.getClass)

  private val buyerTxs: Map[String, List[BuyerTransaction]] = HashMap()

  private val sellerTxs: Map[String, List[SellerTransaction]] = HashMap()


  def sellerTransactions: List[SellerTransactionRow] = sellerTxs.map(t => t._2.map(SellerTransactionRow(t._1, _))).flatten.toList

  def add(tx: Transaction): Unit = {

    def addBuyerTx(newTx: NewTransaction) = {
      log.debug(s"Add buyer transaction to fifo. TxId ${newTx.id}")
      buyerTxs.get(newTx.cryptoSymbol) match {
        case Some(bTxs :+ last) => buyerTxs.put(newTx.cryptoSymbol, last.merge(newTx).map(bTxs :+ _).getOrElse(bTxs :+ last :+ toBuyerTx(newTx)))
        case Some(Nil)       => buyerTxs.put(newTx.cryptoSymbol, List(toBuyerTx(newTx)))
        case None               => buyerTxs.put(newTx.cryptoSymbol, List(toBuyerTx(newTx)))
      }
    }

    def toBuyerTx(newTx: NewTransaction): BuyerTransaction = {
      BuyerTransaction(newTx.exchangeRate, newTx.cryptoAmount, newTx.txValue)
    }

    def addSellerTx(newTx: NewTransaction) = {
      @tailrec
      def addCost(sTx: SellerTransaction, bTxs: List[BuyerTransaction]): (SellerTransaction, List[BuyerTransaction]) = {
        def refundBTxs(sTx: SellerTransaction, bTxs: List[BuyerTransaction]): List[BuyerTransaction] = {
          bTxs.head.refund(sTx) match {
            case Some(rBTx) => rBTx :: bTxs.tail
            case None       => bTxs.tail
          }
        }

        log.debug(s"Recursive call of addCost function. TxId: ${newTx.id}, includeAllCosts ${sTx.includeAllCost}, amount ${sTx.amount}, costAmount ${sTx.costAmount}")
        if (bTxs.isEmpty) {
          log.warn(s"Transaction doesn't include all buy costs! Did you import all buyer transaction? TxId ${newTx.id}.")
          (sTx, bTxs)
        }

        else {
          log.debug(s"Add cost to seller transaction. TxId ${newTx.id}, sTxAmountToCost ${sTx.amountToCost}, bTxAmount ${bTxs.head.amount}")
          sTx.addCost(bTxs.head) match {
            case cSTx if cSTx.includeAllCost => (cSTx, refundBTxs(sTx, bTxs))
            case cSTx                        => addCost(cSTx, refundBTxs(sTx, bTxs))
          }
        }
      }

      log.debug(s"Add seller transaction to fifo. TxId ${newTx.id}")
      val bTxs = buyerTxs.getOrElse(newTx.cryptoSymbol, List())
      val effects = addCost(toSellerTx(newTx), bTxs)

      buyerTxs.put(newTx.cryptoSymbol, effects._2)

      sellerTxs.get(newTx.cryptoSymbol) match {
        case Some(sTxs) => sellerTxs.put(newTx.cryptoSymbol, sTxs :+ effects._1)
        case None       => sellerTxs.put(newTx.cryptoSymbol, List(effects._1))
      }
    }

    def toSellerTx(newTx: NewTransaction): SellerTransaction = {
      SellerTransaction(newTx.id, newTx.exchangeRate, newTx.cryptoAmount, newTx.txValue, CryptoAmount(BigInt(0)), CryptoValue(BigInt(0)), newTx.transactionDateTime)
    }

    log.debug(s"Processing transaction. TxId ${tx.id}, type ${tx.txRole}.")
    val newTx = NewTransaction(tx)
    newTx.txRole match {
      case "BUYER"  => addBuyerTx(newTx)
      case "SELLER" => addSellerTx(newTx)
      case _ => throw new IllegalArgumentException(s"Unknown role value. Acceptable: 'BUYER', 'SELLER'. TxId ${tx.id}.")
    }
  }
}
