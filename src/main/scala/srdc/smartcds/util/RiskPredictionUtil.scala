package srdc.smartcds.util

import io.onfhir.path.FhirPathEvaluator
import org.json4s.JNothing
import org.json4s.JsonDSL._
import org.json4s._

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import srdc.smartcds.cds.client.MLModelClient
import srdc.smartcds.config.LlmConfig
import srdc.smartcds.model.fhir.{Answer, QuestionnaireResponse}

object RiskPredictionUtil {

  /**
   * Builds the ML server request inputs from the QuestionnaireResponse that onfhir-feast's
   * `stage_wp6_model_features` FeatureSet returns (via `$retrieve-feature-values&format=fhir`).
   *
   * The FeatureSet's outputName (= the linkId of every item in that response) is the UK Biobank field code, and
   * the ML server accepts UK Biobank codes directly as feature names, so no feature-name mapping or per-feature
   * logic lives here: every answered item becomes `UKBB code -> value`. All transformations (mappings,
   * aggregations, 'Yes'/'Unknown' conversions, ...) are defined in the feature mapping Excel and generated into
   * the FeatureSet definitions.
   *
   * The response groups features under synthetic parent item(s), so this descends one level, falling back to
   * the top-level item itself when it has no nested `item` array. Items without an answer are left out
   * (the ML model tolerates missing features). Only the first answer of an item is used.
   */
  def extractModelInputs(qr: QuestionnaireResponse): Map[String, Option[Any]] = {
    val leafItems = qr.item.getOrElse(Array()).flatMap(top => top.item.getOrElse(Array(top)))
    leafItems.flatMap { item =>
      item.answer.getOrElse(Array()).headOption.flatMap(answerValue).map(v => item.linkId -> (Some(v): Option[Any]))
    }.toMap
  }

  // Coded answers come back as either valueCoding (feature declares a valueSet) or valueString (it doesn't)
  private def answerValue(a: Answer): Option[Any] =
    a.valueDecimal
      .orElse(a.valueInteger)
      .orElse(a.valueBoolean)
      .orElse(a.valueCoding.map(_.code))
      .orElse(a.valueString)




  def getAllQRAnswers(
                       prefetchKey: String,
                       fhirPathEvaluator: FhirPathEvaluator
                     ): Map[String, Seq[Any]] = {

    val linkIds =
      fhirPathEvaluator.evaluateString(
        s"%cdsPrefetch.$prefetchKey.descendants().where(linkId.exists()).linkId",
        JNothing
      ).distinct

    linkIds.flatMap { linkId =>
      val base =
        s"%cdsPrefetch.$prefetchKey.descendants().where(linkId='$linkId').answer"

      val values: Seq[Any] =
        fhirPathEvaluator.evaluateString(s"$base.valueString", JNothing) ++
          fhirPathEvaluator.evaluateString(s"$base.valueCoding.code", JNothing) ++
          fhirPathEvaluator.evaluateNumerical(s"$base.valueDecimal", JNothing).map(_.toDouble) ++
          fhirPathEvaluator.evaluateNumerical(s"$base.valueInteger", JNothing).map(_.toInt) ++
          fhirPathEvaluator.evaluateBoolean(s"$base.valueBoolean", JNothing)

      if (values.nonEmpty) Some(linkId -> values)
      else None
    }.toMap
  }
}

/**
 * Clean FHIR Observation Builder
 * One observation per disease with risk score and SHAP features as components
 */
object FhirObservationBuilder {

  implicit val formats: DefaultFormats.type = DefaultFormats

  /**
   * Create one observation per disease
   * - Main value: disease risk score
   * - Components: each feature with its SHAP value
   */
  def createDiseaseRiskObservation(
                                    disease: String,
                                    riskScore: Double,
                                    patientId: String,
                                    shapExplanation: Option[MLModelClient.DiseaseShapExplanation] = None,
                                    effectiveDateTime: Option[String] = None
                                  ): JObject = {

    val timestamp = effectiveDateTime.getOrElse(
      ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    )

    val observationId = s"risk-$disease"

    val Seq(rpSystem, rpCode, _*) = LlmConfig.riskPredictionCoding.split("\\|").toSeq

    val baseObservation: JObject =
      ("resourceType" -> "Observation") ~
        ("id" -> observationId) ~
        ("status" -> "final") ~
        ("code" -> (
          ("text" -> disease) ~
            ("coding" -> List(
              ("code" -> rpCode) ~
              ("system" -> rpSystem)
            ))
          )) ~
        ("subject" -> (
          ("reference" -> s"Patient/$patientId")
          )) ~
        ("effectiveDateTime" -> timestamp) ~
        ("valueQuantity" -> (
          ("value" -> riskScore) ~
            ("unit" -> "probability")
          ))

    shapExplanation match {
      case Some(shap) =>
        val components = shap.features
          .filter(_.value.isDefined)
          .map { feature =>
            ("code" -> (
              ("text" -> feature.name)
              )) ~
              ("valueQuantity" -> (
                ("value" -> feature.shap_value)
                ))
          }
          .toList

        baseObservation ~ ("component" -> components)

      case None =>
        baseObservation
    }
  }

  /**
   * Create bundle with all disease observations
   */
  def createObservationBundle(
                               predictions: List[MLModelClient.DiseasePrediction],
                               shapExplanations: Option[List[MLModelClient.DiseaseShapExplanation]],
                               overallRiskScore: Double,
                               patientId: String
                             ): JObject = {

    val timestamp = ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    // one observation per disease
    val observations = predictions.map { pred =>
      val shapExp = shapExplanations.flatMap(_.find(_.disease == pred.disease))
      createDiseaseRiskObservation(pred.disease, pred.risk_score, patientId, shapExp, Some(timestamp))
    }

    val entries = observations.map { obs =>
//      ("fullUrl" -> s"urn:uuid:${(obs \ "id").extract[String]}") ~
        ("resource" -> obs) ~
        ("search" -> ("mode" -> "match"))
    }

    ("resourceType" -> "Bundle") ~
      ("type" -> "searchset") ~
      ("timestamp" -> timestamp) ~
      ("total" -> entries.length) ~
      ("entry" -> entries)
  }

  /**
   * Format disease name for display
   */
  def formatDiseaseName(disease: String): String = {
    disease
      .replace("PHENOTYPE_Incident_10year_", "")
      .replace("_", " ")
      .split(" ")
      .map(_.capitalize)
      .mkString(" ")
  }
}


