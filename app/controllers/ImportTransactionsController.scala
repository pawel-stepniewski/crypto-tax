package controllers

import java.text.SimpleDateFormat

import daos.TransactionDao
import daos.TransactionDao.NewTransaction
import javax.inject.{Inject, Singleton}
import play.api.mvc.{AbstractController, ControllerComponents}

import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source
import scala.util.{Failure, Success, Try}

@Singleton
class ImportTransactionsController @Inject()(transactionDao: TransactionDao,
                                             cc: ControllerComponents)
                                            (implicit ec: ExecutionContext)
  extends AbstractController(cc) {

  private val log = play.api.Logger(this.getClass)

  def importTransactions = Action.async(parse.temporaryFile) { implicit request =>

    val tmpFile = request.body.path.toFile
    Future.sequence(Source.fromFile(tmpFile).getLines().drop(1).map(toNewTransaction(_)).map(transactionDao.insert(_)))
      .map(v => Accepted)
  }

  //TODO: Refactor
  private def toNewTransaction(line: String): NewTransaction = {
    val fields = line.split(';').map(_.replaceAll("\"", "")).map(_.trim)

    val df: SimpleDateFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss")

    Try {

      val txValue = BigInt(fields(6).split("\\.") match {case arr: Array[String] => s"${arr(0)}${if(arr.length == 2) arr(1).padTo(2, "0").mkString else "00" }"})

      val exchangeRate = BigInt(fields(4).split("\\.") match {case arr: Array[String] => s"${arr(0)}${if(arr.length == 2) arr(1).padTo(2, "0").mkString else "00"}"})

      val cryptoAmount = BigInt(fields(5).split("\\.") match {case arr: Array[String] => s"${arr(0)}${if(arr.length == 2) arr(1).padTo(8, "0").mkString else "00000000"}"})

      val commissionBuy =
        if (fields.length == 8 || fields(8).isEmpty) None
        else Some(BigInt(fields(8).replaceAll("\\-", "").split("\\.") match {case arr: Array[String] => s"${arr(0)}${if(arr.length == 2) arr(1).padTo(8, "0").mkString else "00000000"}"}))

      val commissionSell =
        if (fields(7).isEmpty) None
        else Some(BigInt(fields(7).replaceAll("\\-", "").split("\\.") match {case arr: Array[String] => s"${arr(0)}${if(arr.length == 2) arr(1).padTo(2, "0").mkString else "00"}"}))


      NewTransaction(cryptoSymbol = fields(0).substring(0, 3),
        cryptoAmount = cryptoAmount,
        txValue = txValue,
        exchangeRate = exchangeRate,
        txRole = if (fields(2) == "Kupno") "BUYER" else "SELLER",
        commissionSell = commissionSell,
        commissionBuy = commissionBuy,
        transactionDateTime = df.parse(fields(1)))
    } match {
      case Success(v) => v
      case Failure(e) =>
        log.error(s"Parsing row error. TxDate ${df.parse(fields(1))}, fields ${fields.length}", e)
        throw e
    }
  }

}
