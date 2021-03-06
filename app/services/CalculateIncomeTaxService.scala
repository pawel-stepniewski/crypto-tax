package services

import java.util.Date

import daos.TransactionDao
import javax.inject.{Inject, Singleton}
import models.TransactionsFifo
import services.CalculateIncomeTaxService.{CalculateIncomeTaxCmd, CalculateIncomeTaxResult}

import scala.concurrent.ExecutionContext

object CalculateIncomeTaxService {

  case class CalculateIncomeTaxCmd(fromDate: Date, toDate: Date)
  case class CalculateIncomeTaxResult()
}


@Singleton
class CalculateIncomeTaxService @Inject()(transactionDao: TransactionDao)
                                         (implicit ec: ExecutionContext) {


  private val log = play.api.Logger(this.getClass)


  def incomeTax(cmd: CalculateIncomeTaxCmd) = {
    val transactionsFifo: TransactionsFifo = new TransactionsFifo()

    transactionDao.selectAll(cmd.fromDate, cmd.toDate)
      .map(_.map(transactionsFifo.add(_)))
      .map(txs => log.info(s"\nSELLER TRANSACTIONS: \n${transactionsFifo.sellerTransactions.map(_.print).mkString("\n")}"))
      .map(txs => log.info(s"\nBUYER TRANSACTIONS:  \n${transactionsFifo.buyerTransactionsForTest}"))
      .map(txs => CalculateIncomeTaxResult())
  }
}
