package srdc.smartcds.cds.flow

import akka.actor.ActorSystem
import io.onfhir.api.Resource
import io.onfhir.cds.model.CdsResponseBuilder
import io.onfhir.cds.service.CdsServiceRequest
import org.json4s.DefaultFormats
import org.json4s.JsonDSL._
import org.json4s.JsonAST.{JObject, JString}
import org.json4s.jackson.Serialization.write
import srdc.smartcds.cds.client.MLModelClient
import srdc.smartcds.util.{DateTimeUtil, FhirObservationBuilder, QuestionnaireResponseUtil}
import srdc.smartcds.model.fhir.Observation

import scala.concurrent.ExecutionContext
import scala.util.Try

object RiskPredictionFlowExecution {

  implicit val formats: DefaultFormats.type = DefaultFormats

  /**
   * PREFILL FLOW: Generate pre-filled QuestionnaireResponse
   */
  def executePrefillFlow(
                          cdsServiceRequest: CdsServiceRequest,
                          responseBuilder: CdsResponseBuilder
                        )(implicit system: ActorSystem, ec: ExecutionContext): CdsResponseBuilder = {
    try {
      println("[PREFILL FLOW] Starting prefill flow...")

      // Extract patient ID
      val patientId = extractPatientId(cdsServiceRequest)
        .getOrElse {
          println("[PREFILL FLOW] No patient ID found")
          return responseBuilder.withCard(
            _.loadCardWithPostTranslation(
              "card-prefill-error",
              "errorMessage" -> "No patient found in context or prefetch"
            )
          )
        }

      println(s"[PREFILL FLOW] Patient ID: $patientId")

      // Extract observations
      val observations = Try(cdsServiceRequest.getSearchPrefetch("observations").map(_.extract[Observation])).getOrElse(Seq.empty)
      println(s"[PREFILL FLOW] Found ${observations.size} observations")

      // sil debug icin
      observations.take(3).foreach { obs =>
        val code = obs.code.coding.headOption
          .map(c => s"${c.system.getOrElse("?")}|${c.code}")
          .getOrElse("no code")
        val value = obs.valueQuantity.flatMap(_.value).map(_.toString).getOrElse("N/A")
        println(s"[PREFILL FLOW]   Obs: $code = $value")
      }

      // Generate pre-filled QR
      val prefilledQR = QuestionnaireResponseUtil.generatePrefilledResponse(
        questionnaireUrl = "Questionnaire/ukbiobank",
        patientId = patientId,
        observations = observations
      )

      val itemCount = prefilledQR.item.map(_.length).getOrElse(0)
      println(s"[PREFILL FLOW] Generated QR with $itemCount items")

      // Create card with pre-filled QR
      responseBuilder.withCard(
        _.loadCardWithPostTranslation(
          "card-questionnaire-prefill",
          "itemCount" -> itemCount,
          "patientId" -> patientId,
          "prefilledQR" -> write(prefilledQR)
        )
      )

    } catch {
      case e: Exception =>
        println(s"[PREFILL FLOW] Error: ${e.getMessage}")
        e.printStackTrace()
        responseBuilder.withCard(
          _.loadCardWithPostTranslation(
            "card-prefill-error",
            "errorMessage" -> e.getMessage
          )
        )
    }
  }

  /**
   * CALCULATION FLOW: Calculate risk from completed QR
   */
  def executeCalculationFlow(
                              answers: Map[String, Option[Any]],
                              responseBuilder: CdsResponseBuilder,
                              patientId: Option[String] = None
                            )(implicit system: ActorSystem, ec: ExecutionContext): CdsResponseBuilder = {

    println(" Starting calculation flow...")
    val pid = patientId.getOrElse("unknown")

    MLModelClient.getPredictionsWithPlots(answers, Some(pid)) match {
      case Some(prediction) =>
        println(s" Received predictions")
        createRiskCardsWithPlainResponse(prediction, responseBuilder, pid)

      case None =>
        println(" ML service unavailable")
        responseBuilder.withCard(
          _.loadCardWithPostTranslation(
            "card-risk-error",
            "effectiveDate" -> DateTimeUtil.zonedNow(),
            "errorMessage" -> "Unable to calculate risk scores. ML service unavailable."
          )
        )
    }
  }

  private def createRiskCardsWithPlainResponse(response: String, builder: CdsResponseBuilder, pid: String) = {
    builder.withCard(_.loadCardWithPostTranslation("card-risk-prediction",
      "riskPredictionResponse" -> response
    ))
  }

  /**
   * Create risk cards with observations
   */
  private def createRiskCardsWithObservations(
                                               prediction: MLModelClient.PredictionResponse,
                                               responseBuilder: CdsResponseBuilder,
                                               patientId: String
                                             ): CdsResponseBuilder = {

    val overallRisk = 0
    var output = responseBuilder

    // Get top 3 risks for summary
    val topRisks = prediction.predictions
      .sortBy(-_.risk_score)
      .take(3)
      .map(p => s"${FhirObservationBuilder.formatDiseaseName(p.disease)}: ${(p.risk_score * 100).formatted("%.1f")}%")
      .mkString(", ")

    // Create FHIR Bundle with all observations
    val observationBundle = FhirObservationBuilder.createObservationBundle(
      predictions = prediction.predictions,
      shapExplanations = prediction.local_shap,
      overallRiskScore = overallRisk,
      patientId = patientId
    )

    // Main summary card with observation bundle
    output = output.withCard(_.loadCardWithPostTranslation(
      "card-risk-prediction-with-bundle",
      "effectiveDate" -> DateTimeUtil.zonedNow(),
      "overallRiskScore" -> f"${overallRisk * 100}%.2f",
      "diseaseCount" -> prediction.predictions.length,
      "topRisks" -> topRisks,
      "featuresMapped" -> prediction.metadata.map(_.features_received).getOrElse(0),
      "totalFeatures" -> prediction.metadata.map(_.total_final_features).getOrElse(0),
      "observationBundle" -> write(observationBundle)
    ))

    // Individual disease risk cards
    prediction.predictions
      .filter(_.risk_score >= 0.15)
      .sortBy(-_.risk_score)
      .foreach { pred =>
        val diseaseName = FhirObservationBuilder.formatDiseaseName(pred.disease)

        val shapDetails = prediction.local_shap
          .flatMap(_.find(_.disease == pred.disease))
          .map { shap =>
            shap.features
              .filter(_.value.isDefined)
              .sortBy(f => -Math.abs(f.shap_value))
              .take(3)
              .map { f =>
                val impact = if (f.shap_value > 0) "↑" else "↓"
                s"${f.name.replace(" 0.0", "")}: ${f.value.get.formatted("%.1f")}$impact"
              }
              .mkString(", ")
          }
          .getOrElse("")

        output = output.withCard(
          _.loadCardWithPostTranslation(
            "card-disease-risk",
            "effectiveDate" -> DateTimeUtil.zonedNow(),
            "diseaseName" -> diseaseName,
            "riskProbability" -> f"${pred.risk_score * 100}%.2f",
            "diseaseCode" -> pred.disease,
            "patientId" -> patientId,
            "shapDetails" -> shapDetails
          )
        )
      }

    output
  }

  /**
   * Extract patient ID from prefetch
   */
  private def extractPatientId(cdsServiceRequest: CdsServiceRequest): Option[String] = {
    println(s"[PREFILL FLOW] Checking prefetches: ${cdsServiceRequest.prefetches.keys.mkString(", ")}")

    cdsServiceRequest.prefetches.get("patient") match {
      // Patient as List[JObject]
      case Some(list: List[_]) =>
        println(s"[PREFILL FLOW] Patient is List with ${list.size} items")
        list.headOption match {
          case Some(patientJson: JObject) =>
            val patientId = (patientJson \ "id").extractOpt[String]
            println(s"[PREFILL FLOW]  Extracted patient ID: $patientId")
            patientId
          case _ =>
            println("[PREFILL FLOW]  First item not JObject")
            None
        }

      // Patient as single JObject
      case Some(patientJson: JObject) =>
        val patientId = (patientJson \ "id").extractOpt[String]
        println(s"[PREFILL FLOW]  Extracted patient ID from JObject: $patientId")
        patientId

      case other =>
        println(s"[PREFILL FLOW]  Unexpected patient type: ${other.map(_.getClass.getName)}")
        None
    }
  }

}