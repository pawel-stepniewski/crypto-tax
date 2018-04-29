package controllers

import java.util.Date

import controllers.CalculateIncomeTaxController.IncomeTaxRequest
import javax.inject.{Inject, Singleton}
import play.api.libs.functional.syntax._
import play.api.libs.json.{JsPath, Reads}
import play.api.mvc.{AbstractController, ControllerComponents}
import services.CalculateIncomeTaxService
import services.CalculateIncomeTaxService.CalculateIncomeTaxCmd

import scala.concurrent.ExecutionContext

object CalculateIncomeTaxController {

  case class IncomeTaxRequest(from: Date, to: Date)

  private implicit val dateReads = play.api.libs.json.Reads.DefaultDateReads

  private implicit val incomeTaxRequestReads: Reads[IncomeTaxRequest] = (
    (JsPath \ "from").read[Date] and
    (JsPath \ "to").read[Date]
  )(IncomeTaxRequest.apply _)
}

@Singleton
class CalculateIncomeTaxController @Inject()(calculateIncomeTaxService: CalculateIncomeTaxService,
                                             cc: ControllerComponents)
                                            (implicit ec: ExecutionContext)
                                   extends AbstractController(cc) {

  private val log = play.api.Logger(this.getClass)


  def incomeTax = Action.async(parse.json[IncomeTaxRequest]) { implicit request => {
    val req: IncomeTaxRequest = request.body

    log.info(s"IncomeTaxRequest received. From ${req.from}, to ${req.to}")
    calculateIncomeTaxService.incomeTax(CalculateIncomeTaxCmd(req.from, req.to))
      .map(r => Accepted)
  }
  }
}
