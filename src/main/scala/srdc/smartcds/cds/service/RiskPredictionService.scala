package srdc.smartcds.cds.service

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import io.onfhir.cds.api.model.CdsResponse
import io.onfhir.cds.model.CdsException
import io.onfhir.cds.service.{BaseCdsService, CdsServiceContext, CdsServiceRequest}
import org.json4s.DefaultFormats
import org.json4s.jackson.JsonMethods.parse
import srdc.smartcds.cds.client.OnfhirFeastClient
import srdc.smartcds.cds.flow.RiskPredictionFlowExecution
import srdc.smartcds.config.SmartCdsConfig
import srdc.smartcds.model.fhir.QuestionnaireResponse
import srdc.smartcds.util.RiskPredictionUtil

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

/**
 * Minimal CDS Service - just routes to flow logic.
 *
 * Rather than relying on a CDS Hooks prefetch of a QuestionnaireResponse (which required the calling EHR's
 * own FHIR server to already hold one, under a `qrId` this service has no way to independently verify), this
 * service calls onfhir-feast's `$retrieve-feature-values` directly for the `stage_wp6_model_features`
 * FeatureSet - the same mechanism `RiskPredictionFormService` already uses for the prefill questionnaire.
 * `stage_wp6_model_features` reads ONLY from a QuestionnaireResponse already saved against the
 * `stage_wp6_features` Questionnaire, so an empty/failed result from onfhir-feast reliably means no such
 * saved response exists yet for the patient - which is surfaced as an explicit error instead of silently
 * computing a prediction from incomplete data.
 */
class RiskPredictionService(cdsServiceContext: CdsServiceContext)
  extends BaseCdsService(cdsServiceContext) {

  implicit val formats: DefaultFormats.type = DefaultFormats
  implicit val system: ActorSystem = ActorSystem("RiskPredictionService")

  override def executeCds(
                           cdsServiceRequest: CdsServiceRequest
                         )(implicit ex: ExecutionContext): Future[CdsResponse] = {

    val responseBuilder = createResponse(cdsServiceRequest)
    val patientId =
      Try(cdsServiceRequest.getStringContext("patientId")).toOption
        .getOrElse(throw new CdsException(StatusCodes.BadRequest, Some("Missing required context 'patientId'!")))

    Future {
      try {
        println("[MODE] Calculation mode - retrieving model features from onfhir-feast")

        val modelFeaturesQR =
          OnfhirFeastClient
            .retrievePrefilledQuestionnaireResponse(
              patientId = patientId,
              feastServiceUrl = SmartCdsConfig.onfhirFeastUrl,
              dataSourceId = SmartCdsConfig.onfhirFeastDataSourceId,
              featureSetId = SmartCdsConfig.onfhirFeastModelFeatureSetId,
              timeout = SmartCdsConfig.onfhirFeastTimeout
            )
            .getOrElse(throw new CdsException(StatusCodes.NotFound,
              Some(s"No saved questionnaire response found for questionnaire stage_wp6_features for patient " +
                s"$patientId (or onfhir-feast could not be reached) - the WP6 features questionnaire must be " +
                s"completed and saved before a risk prediction can be requested.")))

        val answers = RiskPredictionUtil.extractModelInputs(parse(modelFeaturesQR).extract[QuestionnaireResponse])

        println(s"[MODE] Extracted ${answers.size} answers")

        // Optional context 'diseases': the diseases the user selected (names from risk_prediction_diseases);
        // when missing or empty, risks are calculated for all diseases
        val diseases = cdsServiceRequest.contextParams.get("diseases").flatMap(d => Try(d.extract[Seq[String]]).toOption).filter(_.nonEmpty)
        println(s"[MODE] Selected diseases: ${diseases.map(_.mkString(", ")).getOrElse("all")}")

        RiskPredictionFlowExecution
          .executeCalculationFlow(answers, responseBuilder, Some(patientId), diseases)
          .cdsResponse

      } catch {
        case e: CdsException => throw e
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