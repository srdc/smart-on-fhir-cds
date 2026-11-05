package srdc.smartcds.cds.client

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

/**
 * Client for onfhir-feast's FeatureSet variable retrieval operation.
 *
 * Instead of manually reading FHIR prefetch resources (Observations, Conditions, QuestionnaireResponses, ...)
 * and mapping each of them to a questionnaire answer by hand, this client delegates that entire computation
 * to onfhir-feast, which already knows how to extract/aggregate every feature declared in a FeatureSet
 * definition (e.g. stage_wp6_features) for a given patient and return it as a ready-to-use FHIR
 * QuestionnaireResponse.
 */
object OnfhirFeastClient {

  /**
   * Calls onfhir-feast's:
   *   GET /DataSource/{dataSourceId}/FeatureSet/{featureSetId}/$retrieve-feature-values?subject={patientId}&format=fhir
   *
   * and returns the raw response body, which (with format=fhir) is already a fully-formed FHIR
   * QuestionnaireResponse JSON document with one item per computable feature, pre-filled with whatever
   * value onfhir-feast could resolve for the patient.
   *
   * @param patientId       FHIR id of the subject/patient to compute features for
   * @param feastServiceUrl Base URL of the onfhir-feast REST API, e.g. "http://localhost:8085/onfhir-feast/api"
   * @param dataSourceId    Id of the onfhir-feast DataSource to query (as configured on the onfhir-feast side)
   * @param featureSetId    Id of the FeatureSet definition to compute, e.g. "stage_wp6_features"
   * @param timeout         Max time to wait for onfhir-feast to respond
   * @return Raw QuestionnaireResponse JSON body on success (HTTP 200), None otherwise (error is logged)
   */
  def retrievePrefilledQuestionnaireResponse(
                                               patientId: String,
                                               feastServiceUrl: String,
                                               dataSourceId: String,
                                               featureSetId: String,
                                               timeout: FiniteDuration = 120.seconds
                                             )(implicit system: ActorSystem, ec: ExecutionContext): Option[String] = {

    // Note the "$$" below is a literal "$" - Scala string interpolation escaping, not part of the URL itself
    val uri =
      Uri(s"$feastServiceUrl/DataSource/$dataSourceId/FeatureSet/$featureSetId/$$retrieve-feature-values")
        .withQuery(Uri.Query("subject" -> patientId, "format" -> "fhir"))

    println(s"[onfhir-feast] Calling $$retrieve-feature-values: $uri")

    val httpRequest = HttpRequest(method = HttpMethods.GET, uri = uri)

    try {
      val responseFuture: Future[HttpResponse] = Http().singleRequest(httpRequest)
      val response = Await.result(responseFuture, timeout)

      response.status match {
        case StatusCodes.OK =>
          val bodyFuture = Unmarshal(response.entity).to[String]
          val body = Await.result(bodyFuture, timeout)
          println(s"[onfhir-feast] Received prefilled QuestionnaireResponse (${body.length} chars) for patient $patientId")
          Some(body)
        case status =>
          val bodyFuture = Unmarshal(response.entity).to[String]
          val body = Await.result(bodyFuture, 10.seconds)
          println(s"[onfhir-feast] Error response (${status.intValue()}) for patient $patientId: $body")
          None
      }
    } catch {
      case ex: Exception =>
        println(s"[onfhir-feast] Exception calling $$retrieve-feature-values for patient $patientId: ${ex.getMessage}")
        ex.printStackTrace()
        None
    }
  }
}
