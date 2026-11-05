package srdc.smartcds.cds.service

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import io.onfhir.cds.api.model.CdsResponse
import io.onfhir.cds.model.CdsException
import io.onfhir.cds.service.{BaseCdsService, CdsServiceContext, CdsServiceRequest}
import org.json4s.DefaultFormats
import srdc.smartcds.cds.client.OnfhirFeastClient
import srdc.smartcds.config.SmartCdsConfig

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

/**
 * CDS Service that pre-fills the UK Biobank risk-prediction questionnaire for a patient.
 *
 * Rather than manually reading dozens of FHIR prefetch resources (Observations, Conditions,
 * QuestionnaireResponses, FamilyMemberHistory, ...) and mapping each of them by hand to a questionnaire
 * item/answer (see git history for the old approach), this service delegates the whole computation to
 * onfhir-feast's $retrieve-feature-values operation, which already knows - from the stage_wp6_features
 * FeatureSet definition - how to extract/aggregate every feature for the patient and return it as a
 * ready-to-use FHIR QuestionnaireResponse.
 */
class RiskPredictionFormService(cdsServiceContext: CdsServiceContext)
  extends BaseCdsService(cdsServiceContext) {

  implicit val formats: DefaultFormats.type = DefaultFormats
  implicit val system: ActorSystem = ActorSystem("RiskPredictionFormService")

  override def executeCds(
                           cdsServiceRequest: CdsServiceRequest
                         )(implicit ex: ExecutionContext): Future[CdsResponse] = {

    val responseBuilder = createResponse(cdsServiceRequest)
    val patientId =
      Try(cdsServiceRequest.getStringContext("patientId")).toOption
        .getOrElse(throw new CdsException(StatusCodes.BadRequest, Some("Missing required context 'patientId'!")))

    Future {
      try {
        val prefilledQR =
          OnfhirFeastClient
            .retrievePrefilledQuestionnaireResponse(
              patientId = patientId,
              feastServiceUrl = SmartCdsConfig.onfhirFeastUrl,
              dataSourceId = SmartCdsConfig.onfhirFeastDataSourceId,
              featureSetId = SmartCdsConfig.onfhirFeastFeatureSetId,
              timeout = SmartCdsConfig.onfhirFeastTimeout
            )
            .getOrElse(throw new CdsException(StatusCodes.InternalServerError,
              Some(s"Could not retrieve a prefilled QuestionnaireResponse from onfhir-feast for patient $patientId!")))

        responseBuilder.withCard(
          _.loadCardWithPostTranslation(
            "card-questionnaire-prefill",
            "prefilledQR" -> prefilledQR
          )
        ).cdsResponse
      } catch {
        case e: CdsException => throw e
        case e: Exception =>
          println(s"[ERROR] Exception in RiskPredictionFormService: ${e.getMessage}")
          e.printStackTrace()
          throw new CdsException(StatusCodes.InternalServerError, Some(s"Service error: ${e.getMessage}"))
      }
    }
  }
}
