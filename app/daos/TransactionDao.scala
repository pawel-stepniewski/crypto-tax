package daos

import java.util.Date

import anorm.SqlParser.get
import anorm.{SqlStringInterpolation, ~}
import daos.TransactionDao.{NewTransaction, Transaction, TransactionId}
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
  case class NewTransaction(cryptoSymbol: String,
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

  def insert(nTx: NewTransaction): Future[TransactionId] = Future {
    db.withConnection { implicit connection =>
      val id: Option[Long] =
        SQL"""
              insert into TRANSACTIONS(CRYPTO_SYMBOL,
                                       CRYPTO_AMOUNT,
                                       TX_VALUE,
                                       EXCHANGE_RATE,
                                       TX_ROLE,
                                       COMMISSION_SELL,
                                       COMMISSION_BUY,
                                       TX_DATETIME)
              values(${nTx.cryptoSymbol},
                     ${nTx.cryptoAmount},
                     ${nTx.txValue},
                     ${nTx.exchangeRate},
                     ${nTx.txRole},
                     ${nTx.commissionSell},
                     ${nTx.commissionBuy},
                     ${nTx.transactionDateTime})
          """
          .executeInsert()

      if (id.isEmpty) {
        log.error(s"Query returns empty id. Record might have been inserted!")
        throw new RuntimeException("Query returns empty id. Record might have been inserted!")
      }

      TransactionId(id.get)
    }
  }(ec)

}
