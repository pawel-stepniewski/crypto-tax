package daos

import java.util.Date

import anorm.SqlParser.get
import anorm.{SqlStringInterpolation, ~}
import daos.TransactionDao.Transaction
import javax.inject.{Inject, Singleton}
import play.api.db.DBApi

import scala.concurrent.Future

object TransactionDao {

  case class TransactionId(id: Long)
  case class Transaction(id: TransactionId,
                         cryptoSymbol: String,
                         cryptoAmount: BigInt,
                         txValue: BigInt,
                         exchangeRate: BigInt,
                         txRole: String,
                         commissionSell: Option[BigInt],
                         commissionBuy: Option[BigInt],
                         transactionDateTime: Date)

  val transactionRowParser = {
    get[Long]("ID") ~
    get[String]("CRYPTO_SYMBOL") ~
    get[BigInt]("CRYPTO_AMOUNT") ~
    get[BigInt]("TX_VALUE") ~
    get[BigInt]("EXCHANGE_RATE") ~
    get[String]("TX_ROLE") ~
    get[Option[BigInt]]("COMMISSION_SELL") ~
    get[Option[BigInt]]("COMMISSION_BUY") ~
    get[Date]("TX_DATETIME") map {
    case id ~ cryptoSymbol ~ cryptoAmount ~ txValue ~ exchangeRate ~ txRole ~ commissionSell ~ commissionBuy ~ txDateTime =>
        Transaction(TransactionId(id), cryptoSymbol, cryptoAmount, txValue, exchangeRate, txRole, commissionSell, commissionBuy, txDateTime)
    }

  }
}


@Singleton
class TransactionDao @Inject()(dbapi: DBApi)(implicit ec: DatabaseExecutionContext) {

  private val log = play.api.Logger(this.getClass)

  private val db = dbapi.database("crypto_tax")

  def selectAll(fromDate: Date, toDate:Date): Future[List[Transaction]] = {
    Future {
      db.withConnection( implicit connection =>
        SQL"""
              select *
              from TRANSACTIONS
              where TX_DATETIME between ${fromDate} and ${toDate}
              order by TX_DATETIME
          """
          .as(TransactionDao.transactionRowParser.*)
      )
    }(ec)
  }

}
