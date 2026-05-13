package srdc.smartcds.cds.client

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.json4s.jackson.Serialization.write

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

object MLModelClient {

  implicit val formats: DefaultFormats.type = DefaultFormats

  case class LocalPlotRequest(
                               features: Map[String, Any],
                               patient_id: Option[String] = None,
                               diseases: Seq[String] = Seq(
                                 "Anxiety",
                                 "Asthma",
                                 "Atrial_Fibrillation",
                                 "COPD",
                                 "Cancer",
                                 "Chronic_Kidney_Disease",
                                 "Chronic_Liver_Disease",
                                 "Coronary_Heart_Disease",
                                 "Dementia_ICD10",
                                 "Depression",
                                 "Diabetes_Mellitus",
                                 "Epilepsy",
                                 "Heart_Failure",
                                 "Hyperlipidemia",
                                 "Hypertension",
                                 "Hypothyroidism",
                                 "Multiple_Sclerosis",
                                 "Obesity",
                                 "Osteoarthritis",
                                 "Osteoporosis",
                                 "Parkinsons",
                                 "Peripheral_Vascular_Disease",
                                 "Rheumatoid_Arthritis",
                                 "Severe_Mental_Illness",
                                 "Sleep_Apnea",
                                 "Stroke_Cerebrovascular",
                                 "Substance_Use_Disorder"
                               ),
                               plot_types: Seq[String] = Seq("waterfall")
                             )

  // Request model (unchanged)
  case class PredictionRequest(
                                features: Map[String, Any],
                                patient_id: Option[String] = None,
                                diseases: Seq[String] = Seq(
                                  "Anxiety",
                                  "Asthma",
                                  "Atrial_Fibrillation",
                                  "COPD",
                                  "Cancer",
                                  "Chronic_Kidney_Disease",
                                  "Chronic_Liver_Disease",
                                  "Coronary_Heart_Disease",
                                  "Dementia_ICD10",
                                  "Depression",
                                  "Diabetes_Mellitus",
                                  "Epilepsy",
                                  "Heart_Failure",
                                  "Hyperlipidemia",
                                  "Hypertension",
                                  "Hypothyroidism",
                                  "Multiple_Sclerosis",
                                  "Obesity",
                                  "Osteoarthritis",
                                  "Osteoporosis",
                                  "Parkinsons",
                                  "Peripheral_Vascular_Disease",
                                  "Rheumatoid_Arthritis",
                                  "Severe_Mental_Illness",
                                  "Sleep_Apnea",
                                  "Stroke_Cerebrovascular",
                                  "Substance_Use_Disorder"
                                ),
                                include_shap: Boolean = true
                              )

  case class DiseasePrediction(
                                disease: String,
                                risk_score: Double
                              )

  case class FeatureShapValue(
                               name: String,
                               value: Option[Double],
                               shap_value: Double
                             )

  case class DiseaseShapExplanation(
                                     disease: String,
                                     base_value: Double,
                                     features: List[FeatureShapValue]
                                   )

  case class Metadata(
                       features_received: Int,
                       total_final_features: Int,
                       shap_included: Boolean
                     )

  case class PredictionResponse(
                                 patient_id: String,
                                 predictions: List[DiseasePrediction],
                                 local_shap: Option[List[DiseaseShapExplanation]],
                                 metadata: Option[Metadata]
                               )

  /**
   * Call ML model and get predictions for all 14 diseases with SHAP explanations
   */
  def getPredictionsWithPlots(
                      answers: Map[String, Option[Any]],
                      patientId: Option[String] = None,
                      mlServiceUrl: String = "http://localhost:8001"
                    )(implicit system: ActorSystem, ec: ExecutionContext): Option[String] = {

    println(s"[ML Model] Calling ML service at $mlServiceUrl")
    println(s"[ML Model] Received ${answers.size} answers")

    // Flatten answers - take first value from each Seq
    val flatFeatures: Map[String, Any] = answers.flatMap {
      case (linkId, values) if values.nonEmpty =>
        val value = values.head
        val convertedValue = value match {
          case n: Number => n
          case s: String =>
            try {
              if (s.contains(".")) s.toDouble else s.toInt
            } catch {
              case _: NumberFormatException => s
            }
          case other => other
        }

        println(s"[ML Model] Feature $linkId = $convertedValue")
        Some(linkId -> convertedValue)
      case _ => None
    }

    println(s"[ML Model] Extracted ${flatFeatures.size} features")

    val requestBody = LocalPlotRequest(
      features = flatFeatures,
      patient_id = patientId
    )

    val requestJson = write(requestBody)
    println(s"[ML Model] Request JSON: $requestJson")

    val httpRequest = HttpRequest(
      method = HttpMethods.POST,
      uri = s"$mlServiceUrl/models/clinical_xgboost/plots/local",
      entity = HttpEntity(
        ContentTypes.`application/json`,
        requestJson
      )
    )

    try {
      println(s"[ML Model] Sending HTTP request...")
      val responseFuture: Future[HttpResponse] = Http().singleRequest(httpRequest)
      val response = Await.result(responseFuture, 30.seconds)

      println(s"[ML Model] Received response with status: ${response.status}")

      response.status match {
        case StatusCodes.OK =>
          val bodyFuture = Unmarshal(response.entity).to[String]
          Some(Await.result(bodyFuture, 10.seconds))
        case status =>
          val bodyFuture = Unmarshal(response.entity).to[String]
          val body = Await.result(bodyFuture, 10.seconds)
          println(s"[ML Model] Error response (${status.intValue()}): $body")
          None
      }
    } catch {
      case ex: Exception =>
        println(s"[ML Model] Exception calling ML service: ${ex.getMessage}")
        ex.printStackTrace()
        None
    }
  }

  /**
   * Call ML model and get predictions for all 14 diseases with SHAP explanations
   */
  def getPredictions(
                      answers: Map[String, Option[Any]],
                      patientId: Option[String] = None,
                      mlServiceUrl: String = "http://localhost:8001",
                      includeShap: Boolean = true
                    )(implicit system: ActorSystem, ec: ExecutionContext): Option[PredictionResponse] = {

    println(s"[ML Model] Calling ML service at $mlServiceUrl")
    println(s"[ML Model] Received ${answers.size} answers")

    // Flatten answers - take first value from each Seq
    val flatFeatures: Map[String, Any] = answers.flatMap {
      case (linkId, values) if values.nonEmpty =>
        val value = values.head
        val convertedValue = value match {
          case n: Number => n
          case s: String =>
            try {
              if (s.contains(".")) s.toDouble else s.toInt
            } catch {
              case _: NumberFormatException => s
            }
          case other => other
        }

        println(s"[ML Model] Feature $linkId = $convertedValue")
        Some(linkId -> convertedValue)
      case _ => None
    }

    println(s"[ML Model] Extracted ${flatFeatures.size} features")

    val requestBody = PredictionRequest(
      features = flatFeatures,
      patient_id = patientId,
      include_shap = includeShap
    )

    val requestJson = write(requestBody)
    println(s"[ML Model] Request JSON: $requestJson")

    val httpRequest = HttpRequest(
      method = HttpMethods.POST,
      uri = s"$mlServiceUrl/models/clinical_xgboost/predict",
      entity = HttpEntity(
        ContentTypes.`application/json`,
        requestJson
      )
    )

    try {
      println(s"[ML Model] Sending HTTP request...")
      val responseFuture: Future[HttpResponse] = Http().singleRequest(httpRequest)
      val response = Await.result(responseFuture, 30.seconds)

      println(s"[ML Model] Received response with status: ${response.status}")

      response.status match {
        case StatusCodes.OK =>
          val bodyFuture = Unmarshal(response.entity).to[String]
          val body = Await.result(bodyFuture, 10.seconds)
          println(s"[ML Model] Response body length: ${body.length} chars")

          val json = parse(body)
          val predictions = json.extract[PredictionResponse]

          println(s"[ML Model] Successfully parsed predictions for ${predictions.predictions.length} diseases")

          // Log metadata if available
          predictions.metadata.foreach { meta =>
            println(s"[ML Model] Metadata: ${meta.features_received}/${meta.total_final_features} features mapped")
          }

          // Log top 3 highest risk diseases
          val topRisks = predictions.predictions
            .sortBy(-_.risk_score)  // Changed from risk_probability
            .take(14)

          if (topRisks.exists(_.risk_score > 0)) {
            println(s"[ML Model] Risks:")
            topRisks.foreach { pred =>
              println(s"   - ${pred.disease}: ${(pred.risk_score * 100).formatted("%.2f")}%")

              // Log SHAP info if available
              if (includeShap && predictions.local_shap.isDefined) {
                predictions.local_shap.get
                  .find(_.disease == pred.disease)
                  .foreach { shapExp =>
                    println(s"     Base value: ${shapExp.base_value}")
                    println(s"     Top contributing factors:")

                    // Get top 5 features by absolute SHAP value
                    val topFeatures = shapExp.features
                      .filter(_.value.isDefined)  // Only include non-missing features
                      .sortBy(f => -Math.abs(f.shap_value))
                      .take(5)

                    topFeatures.foreach { feat =>
                      val impact = if (feat.shap_value > 0) "increases" else "decreases"
                      println(s"       - ${feat.name} = ${feat.value.get}: SHAP = ${feat.shap_value} ($impact risk)")
                    }
                  }
              }
            }
          }

          Some(predictions)

        case status =>
          val bodyFuture = Unmarshal(response.entity).to[String]
          val body = Await.result(bodyFuture, 10.seconds)
          println(s"[ML Model] Error response (${status.intValue()}): $body")
          None
      }
    } catch {
      case ex: Exception =>
        println(s"[ML Model] Exception calling ML service: ${ex.getMessage}")
        ex.printStackTrace()
        None
    }
  }

  /**
   * Helper method to get high-risk diseases (you define threshold)
   */
  def getHighRiskDiseases(response: PredictionResponse, threshold: Double = 0.30): List[DiseasePrediction] = {
    response.predictions.filter(_.risk_score >= threshold)
  }

  /**
   * Helper method to get medium-risk diseases
   */
  def getMediumRiskDiseases(response: PredictionResponse,
                            lowThreshold: Double = 0.15,
                            highThreshold: Double = 0.30): List[DiseasePrediction] = {
    response.predictions.filter(p => p.risk_score >= lowThreshold && p.risk_score < highThreshold)
  }

  /**
   * Helper method to get low-risk diseases
   */
  def getLowRiskDiseases(response: PredictionResponse, threshold: Double = 0.15): List[DiseasePrediction] = {
    response.predictions.filter(_.risk_score < threshold)
  }

  /**
   * Get predictions sorted by risk (highest first)
   */
  def getSortedByRisk(response: PredictionResponse): List[DiseasePrediction] = {
    response.predictions.sortBy(-_.risk_score)
  }

  /**
   * Get SHAP explanation for a specific disease
   */
  def getShapExplanationForDisease(response: PredictionResponse, disease: String): Option[DiseaseShapExplanation] = {
    response.local_shap.flatMap(_.find(_.disease == disease))
  }

  /**
   * Get the most influential features for a specific disease (by SHAP value)
   */
  def getTopFeaturesForDisease(response: PredictionResponse, disease: String, topN: Int = 5): List[FeatureShapValue] = {
    getShapExplanationForDisease(response, disease)
      .map { shapExp =>
        shapExp.features
          .filter(_.value.isDefined)  // Only include non-missing features
          .sortBy(f => -Math.abs(f.shap_value))
          .take(topN)
      }
      .getOrElse(List.empty)
  }

  /**
   * Get features that increase risk for a disease (positive SHAP values)
   */
  def getRiskIncreasingFeatures(response: PredictionResponse, disease: String, topN: Int = 5): List[FeatureShapValue] = {
    getShapExplanationForDisease(response, disease)
      .map { shapExp =>
        shapExp.features
          .filter(f => f.value.isDefined && f.shap_value > 0)
          .sortBy(-_.shap_value)
          .take(topN)
      }
      .getOrElse(List.empty)
  }

  /**
   * Get features that decrease risk for a disease (negative SHAP values)
   */
  def getRiskDecreasingFeatures(response: PredictionResponse, disease: String, topN: Int = 5): List[FeatureShapValue] = {
    getShapExplanationForDisease(response, disease)
      .map { shapExp =>
        shapExp.features
          .filter(f => f.value.isDefined && f.shap_value < 0)
          .sortBy(_.shap_value)  // Sort ascending (most negative first)
          .take(topN)
      }
      .getOrElse(List.empty)
  }

  /**
   * Get overall most influential features across all diseases
   */
  def getMostInfluentialFeaturesOverall(response: PredictionResponse, topN: Int = 10): List[(String, Double)] = {
    response.local_shap match {
      case Some(shapExps) =>
        // Aggregate SHAP values across all diseases
        val featureImpacts = scala.collection.mutable.Map[String, Double]()

        shapExps.foreach { shapExp =>
          shapExp.features
            .filter(_.value.isDefined)
            .foreach { feat =>
              val currentImpact = featureImpacts.getOrElse(feat.name, 0.0)
              featureImpacts(feat.name) = currentImpact + Math.abs(feat.shap_value)
            }
        }

        // Sort by total impact and return top N
        featureImpacts.toList
          .sortBy(-_._2)
          .take(topN)

      case None => List.empty
    }
  }

  /**
   * Generate a risk summary with categorization
   */
  def getRiskSummary(response: PredictionResponse): Map[String, Any] = {
    val highRisk = getHighRiskDiseases(response)
    val mediumRisk = getMediumRiskDiseases(response)
    val lowRisk = getLowRiskDiseases(response)

    Map(
      "overall_risk_score" -> 0,
      "overall_risk_percentage" -> 0,
      "high_risk_count" -> highRisk.length,
      "high_risk_diseases" -> highRisk.map(_.disease),
      "medium_risk_count" -> mediumRisk.length,
      "medium_risk_diseases" -> mediumRisk.map(_.disease),
      "low_risk_count" -> lowRisk.length,
      "patient_id" -> response.patient_id,
      "total_diseases_analyzed" -> response.predictions.length
    )
  }

  /**
   * Check ML service health
   */
  def checkHealth(mlServiceUrl: String = "http://localhost:8001")
                 (implicit system: ActorSystem, ec: ExecutionContext): Boolean = {
    val httpRequest = HttpRequest(
      method = HttpMethods.GET,
      uri = s"$mlServiceUrl/health"
    )

    try {
      val responseFuture: Future[HttpResponse] = Http().singleRequest(httpRequest)
      val response = Await.result(responseFuture, 5.seconds)

      response.status match {
        case StatusCodes.OK =>
          val bodyFuture = Unmarshal(response.entity).to[String]
          val body = Await.result(bodyFuture, 5.seconds)
          println(s"[ML Model] Health check: $body")
          true
        case _ =>
          println(s"[ML Model] Health check failed with status: ${response.status}")
          false
      }
    } catch {
      case ex: Exception =>
        println(s"[ML Model] Health check exception: ${ex.getMessage}")
        false
    }
  }

  /**
   * Get list of all diseases the model can predict
   */
  def getDiseases(mlServiceUrl: String = "http://localhost:8001")
                 (implicit system: ActorSystem, ec: ExecutionContext): Option[List[String]] = {
    val httpRequest = HttpRequest(
      method = HttpMethods.GET,
      uri = s"$mlServiceUrl/diseases"
    )

    try {
      val responseFuture: Future[HttpResponse] = Http().singleRequest(httpRequest)
      val response = Await.result(responseFuture, 5.seconds)

      response.status match {
        case StatusCodes.OK =>
          val bodyFuture = Unmarshal(response.entity).to[String]
          val body = Await.result(bodyFuture, 5.seconds)
          val json = parse(body)
          val diseases = (json \ "diseases").extract[List[String]]
          println(s"[ML Model] Available diseases (${diseases.length}): ${diseases.mkString(", ")}")
          Some(diseases)
        case _ =>
          println(s"[ML Model] Failed to get diseases: ${response.status}")
          None
      }
    } catch {
      case ex: Exception =>
        println(s"[ML Model] Exception getting diseases: ${ex.getMessage}")
        None
    }
  }

  /**
   * Convert a single DiseasePrediction to a simple Map (for easy serialization)
   */
  def predictionToMap(prediction: DiseasePrediction): Map[String, Any] = {
    Map(
      "disease" -> prediction.disease,
      "risk_score" -> prediction.risk_score,
      "risk_percentage" -> (prediction.risk_score * 100).formatted("%.2f")
    )
  }

  /**
   * Convert SHAP explanation to a simple Map (for easy serialization)
   */
  def shapExplanationToMap(shapExp: DiseaseShapExplanation, topN: Int = 10): Map[String, Any] = {
    val topFeatures = shapExp.features
      .filter(_.value.isDefined)
      .sortBy(f => -Math.abs(f.shap_value))
      .take(topN)
      .map { feat =>
        Map(
          "name" -> feat.name,
          "value" -> feat.value.get,
          "shap_value" -> feat.shap_value,
          "impact" -> (if (feat.shap_value > 0) "increases_risk" else "decreases_risk")
        )
      }

    Map(
      "disease" -> shapExp.disease,
      "base_value" -> shapExp.base_value,
      "top_features" -> topFeatures
    )
  }

  /**
   * Print a formatted risk report to console
   */
  def printRiskReport(response: PredictionResponse): Unit = {
    println("\n" + "="*60)
    println(s"Risk Assessment Report for Patient: ${response.patient_id}")
    println("="*60)

    val summary = getRiskSummary(response)
    println(s"\nOverall Risk Score: ${summary("overall_risk_percentage")}%")
    println(s"Total Diseases Analyzed: ${summary("total_diseases_analyzed")}")

    // High risk diseases
    if (summary("high_risk_count").asInstanceOf[Int] > 0) {
      println(s"\n⚠️  HIGH RISK Diseases (${summary("high_risk_count")}):")
      getHighRiskDiseases(response).foreach { pred =>
        println(f"   • ${pred.disease}%-40s ${pred.risk_score * 100}%.2f%%")
      }
    }

    // Medium risk diseases
    if (summary("medium_risk_count").asInstanceOf[Int] > 0) {
      println(s"\n⚡ MEDIUM RISK Diseases (${summary("medium_risk_count")}):")
      getMediumRiskDiseases(response).foreach { pred =>
        println(f"   • ${pred.disease}%-40s ${pred.risk_score * 100}%.2f%%")
      }
    }

    // Most influential features overall
    if (response.local_shap.isDefined) {
      println("\n Most Influential Factors Overall:")
      getMostInfluentialFeaturesOverall(response, 5).foreach { case (feature, impact) =>
        println(f"   • ${feature}%-40s Impact: ${impact}%.4f")
      }
    }

    println("\n" + "="*60 + "\n")
  }
}