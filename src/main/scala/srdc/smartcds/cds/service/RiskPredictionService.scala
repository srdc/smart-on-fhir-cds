package srdc.smartcds.cds.service

import akka.actor.ActorSystem
import io.onfhir.cds.api.model.CdsResponse
import io.onfhir.cds.service.{BaseCdsService, CdsServiceContext, CdsServiceRequest}
import org.json4s.DefaultFormats
import srdc.smartcds.cds.flow.RiskPredictionFlowExecution
import srdc.smartcds.model.fhir.QuestionnaireResponse
import srdc.smartcds.util.RiskPredictionUtil

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

/**
 * Minimal CDS Service - just routes to flow logic
 */
class RiskPredictionService(cdsServiceContext: CdsServiceContext)
  extends BaseCdsService(cdsServiceContext) {

  implicit val formats: DefaultFormats.type = DefaultFormats
  implicit val system: ActorSystem = ActorSystem("RiskPredictionService")

  override def executeCds(
                           cdsServiceRequest: CdsServiceRequest
                         )(implicit ex: ExecutionContext): Future[CdsResponse] = {

    val fhirPathEvaluator = getFhirPathEvaluator(cdsServiceRequest)
    val responseBuilder = createResponse(cdsServiceRequest)
    val patientId = Try(cdsServiceRequest.getStringContext("patientId")).toOption

    Future {
      try {
          // CALCULATION MODE: Delegate to flow
          println("[MODE] Calculation mode - delegating to flow")
          val answers = RiskPredictionUtil.mapQrToFeatures(cdsServiceRequest.getReadPrefetch("qr").extract[QuestionnaireResponse])

          println(s"[MODE] Extracted ${answers.size} answers")

          RiskPredictionFlowExecution
            .executeCalculationFlow(answers, responseBuilder, patientId)
            .cdsResponse

      } catch {
        case e: Exception =>
          println(s"[ERROR] Exception in service: ${e.getMessage}")
          e.printStackTrace()

          responseBuilder.withCard(
            _.loadCardWithPostTranslation(
              "card-risk-error",
              "errorMessage" -> s"Service error: ${e.getMessage}"
            )
          ).cdsResponse
      }
    }
  }
}