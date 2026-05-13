package srdc.smartcds.util

import io.onfhir.path.FhirPathEvaluator
import org.json4s.JNothing
import org.json4s.JsonDSL._
import org.json4s._

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import srdc.smartcds.cds.client.MLModelClient
import srdc.smartcds.config.LlmConfig
import srdc.smartcds.model.fhir.QuestionnaireResponse
import srdc.smartcds.model.UKBiobankFeatures._

object RiskPredictionUtil {

  def mapQrToFeatures(qr: QuestionnaireResponse) = {
    val items = qr.item.get.flatMap(_.item).flatMap(_.toSeq).map(item => item.linkId -> item.answer).toMap
    def getInteger(key: String) = items.get(key).flatMap(_.flatMap(_.headOption.flatMap(_.valueInteger)))
    def getDecimal(key: String) = items.get(key).flatMap(_.flatMap(_.headOption.flatMap(_.valueDecimal)))
    def getCode(key: String) = items.get(key).flatMap(_.flatMap(_.headOption.flatMap(_.valueCoding.map(_.code))))
    def matchesCode(code: String, truthfulCodes: String*) =
      if (truthfulCodes.contains(code)) Some(1) else if (code == "-3") None else Some(0)

    val waistHipRatio = (getDecimal(WAIST_CIRCUMFERENCE), getDecimal(HIP_CIRCUMFERENCE)) match {
      case (w, h) if w.nonEmpty && h.nonEmpty => Some(w.get/h.get)
      case _ => None
    }

    Map(
      "Age 0.0" -> getInteger(AGE),
//      "BMI 0.0" -> getDecimal(BMI),
      "C-reactive protein 0.0" -> getDecimal(CRP),
      "Cholesterol 0.0" -> getDecimal(CHOLESTEROL),
      "Cystatin C 0.0" -> getDecimal(CYSTATIN_C),
      "Diastolic blood pressure 0.0" -> getDecimal(DIASTOLIC_BLOOD_PRESSURE),
//      "Diastolic blood pressure 0.1" -> ,
      "Gamma glutamyltransferase 0.0" -> getDecimal(GAMMA_GLUTAMYLTRANSFERASE),
      "Getting up 0.0_num" -> getCode(GETTING_UP),
      "HbA1c 0.0" -> getDecimal(HBA1C),
      "IMD Employment score 0.0" -> getCode(IMD_EMPLOYMENT_SCORE),
      "IMD Health score 0.0" -> getCode(IMD_HEALTH_SCORE),
      "LDL direct 0.0" -> getDecimal(LDL),
      "Sleep duration 0.0" -> getDecimal(SLEEP),
      "Systolic blood pressure 0.0" -> getDecimal(SYSTOLIC_BLOOD_PRESSURE),
      "Waist circumference 0.0" -> getDecimal(WAIST_CIRCUMFERENCE),
      "Day nap 0.0_ord" -> getCode(NAP),
      "Sleeplessness 0.0_ord" -> getCode(INSOMNIA),
      "Accomodation own/rent 0.0_recoded_Renter" -> getCode(ACCOMMODATION).flatMap(matchesCode(_, "3", "4")),
      "Coffee type 0.0_Ground coffee (include espresso, filter etc)" -> getCode(COFFEE_TYPE).flatMap(matchesCode(_, "3")),
      "Dental problems0 0.0_Dentures" -> getCode(DENTAL_PROBLEMS).flatMap(matchesCode(_, "6")),
      "Drinker status 0.0_Previous" -> getCode(ALCOHOL_DRINKER_STATUS).flatMap(matchesCode(_, "1")),
      "Employment0 0.0_recoded_Employed" -> getCode(EMPLOYMENT).flatMap(matchesCode(_, "1")),
//      "Employment0 0.0_recoded_Other" -> (if (getCode(EMPLOYMENT).contains("?")) 1 else 0), // NO OTHER OPTION
      "Fed-up feelings 0.0_Yes" -> getCode(FED_UP_FEELINGS).flatMap(matchesCode(_, "1")),
      "Fractured bones 0.0_Yes" -> getCode(FRACTURED_BONES).flatMap(matchesCode(_, "1")),
      "Hearing difficulty 0.0_No" -> getCode(HEARING_DIFFICULTY).flatMap(matchesCode(_, "0")),
      "Loneliness 0.0_Yes" -> getCode(LONELINESS).flatMap(matchesCode(_, "1")),
      "Miserableness 0.0_Yes" -> getCode(MISERABLENESS).flatMap(matchesCode(_, "1")),
      "Mood swings 0.0_Yes" -> getCode(MOOD_SWING).flatMap(matchesCode(_, "1")),
      "Mother illnesses1 0.1_None of the above (group 2)" -> getCode(MOTHER_ILLNESS_2).flatMap(matchesCode(_, "-27")),
      "Nerves 0.0_Yes" -> getCode(NERVES).flatMap(matchesCode(_, "1")),
      "Pain0 0.0_None of the above" -> getCode(PAIN).flatMap(matchesCode(_, "-7")),
      "Pain0 0.0_Pain all over the body" -> getCode(PAIN).flatMap(matchesCode(_, "8")),
      "Qualification0 0.0_recoded_None" -> getCode(QUALIFICATION).flatMap(matchesCode(_, "-7")),
      "Sensitivity 0.0_Yes" -> getCode(SENSITIVITY).flatMap(matchesCode(_, "1")),
      "Sibling illnesses0 0.0_None of the above (group 1)" -> getCode(SIBLING_ILLNESS_1).flatMap(matchesCode(_, "-17")),
      "Smoking status 0.0_Never" -> getCode(SMOKING_STATUS).flatMap(matchesCode(_, "0")),
      "Tobacco smoking 0.0_Yes, on most or all days" -> getCode(TOBACCO_SMOKING).flatMap(matchesCode(_, "1")),
      "Worrier feelings 0.0_Yes" -> getCode(WORRIER_FEELINGS).flatMap(matchesCode(_, "1")),
      "Body fat % 0.0" -> getDecimal(BODY_FAT_PERCENTAGE),
      "Accomodation own/rent 0.0_recoded_Owner" -> getCode(ACCOMMODATION).flatMap(matchesCode(_, "1", "2")),
      "Mother illnesses1 0.1_None of the above (group 1)" -> getCode(MOTHER_ILLNESS_1).flatMap(matchesCode(_, "-17")),
      "High light scatter reticulocyte pct 0.0" -> getDecimal(HIGH_LIGHT_SCATTER_RETICULOCYTE_PCT),
      "Waist-to-hip ratio 0.0" -> waistHipRatio,
      "Dental problems0 0.0_None of the above" -> getCode(DENTAL_PROBLEMS).flatMap(matchesCode(_, "-7")),
    )
  }



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


