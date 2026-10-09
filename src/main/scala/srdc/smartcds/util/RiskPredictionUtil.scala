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
    def getDecimal(key: String): Option[Double] =
      items.get(key).flatMap(
        _.flatMap(
          _.headOption.flatMap { answer =>
            answer.valueDecimal
              .map(_.toDouble)
              .orElse(answer.valueInteger.map(_.toDouble))
          }
        )
      )
    def getCode(key: String) = items.get(key).flatMap(_.flatMap(_.headOption.flatMap(_.valueCoding.map(_.code))))
    def mapCategoricalCode(
      key: String,
      feature: String,
      codeMap: Map[String, String]
    ): Option[String] =
      getCode(key).map { code =>
        codeMap.getOrElse(
          code,
          throw new IllegalArgumentException(
            s"Unsupported Questionnaire code '$code' for feature '$feature'"
          )
        )
      }

    def matchesCode(code: String, truthfulCodes: String*) =
      if (truthfulCodes.contains(code)) Some(1) else if (code == "-3") None else Some(0)

    def getCodes(key: String): Seq[String] =
      items
        .get(key)
        .flatten
        .map(
          answers =>
            answers.toSeq.flatMap(answer => answer.valueCoding.map(_.code))
        )
        .getOrElse(Seq.empty[String])

    def matchesAnyCode(codes: Seq[String], truthfulCodes: String*): Option[Int] =
      if (codes.isEmpty) None
      else if (codes.exists(truthfulCodes.contains)) Some(1)
      else if (codes.contains("-3")) None
      else Some(0)

    val mentalYesNoMap = Map(
      "1" -> "Yes",
      "2" -> "No",
      "-1" -> "Unknown",
      "-3" -> "Unknown"
    )

    val accommodationMap = Map(
      "1" -> "Owner",
      "2" -> "Owner",
      "3" -> "Renter",
      "4" -> "Renter",
      "5" -> "Owner",
      "6" -> "Other",
      "-7" -> "Other",
      "-3" -> "Unknown"
    )

    val coffeeTypeMap = Map(
      "1" -> "Decaffeinated coffee (any type)",
      "2" -> "Instant coffee",
      "3" -> "Ground coffee (include espresso, filter etc)",
      "4" -> "Other type of coffee",
      "-1" -> "Unknown",
      "-3" -> "Unknown"
    )

    val employmentMap = Map(
      "1" -> "Employed",
      "2" -> "Retired",
      "3" -> "Other",
      "4" -> "Disabled",
      "5" -> "Unemployed",
      "6" -> "Other",
      "7" -> "Student",
      "-7" -> "Other",
      "-3" -> "Other"
    )

    val fracturedBonesMap = Map(
      "1" -> "Yes",
      "0" -> "No",
      "-1" -> "Unknown",
      "-3" -> "Unknown"
    )

    val hearingDifficultyMap = Map(
      "1" -> "Yes",
      "0" -> "No",
      "99" -> "Yes",
      "-1" -> "Unknown",
      "-3" -> "Unknown"
    )

    val qualificationMap = Map(
      "1" -> "Degree",
      "2" -> "Secondary",
      "3" -> "Secondary",
      "4" -> "Secondary",
      "5" -> "Vocational",
      "6" -> "Vocational",
      "-7" -> "No qualifications",
      "-3" -> "Other"
    )

    val smokingStatusMap = Map(
      "0" -> "Never",
      "1" -> "Previous",
      "2" -> "Current",
      "-3" -> "Unknown"
    )

    val tobaccoSmokingMap = Map(
      "0" -> "No",
      "1" -> "Yes, on most or all days",
      "2" -> "Only occasionally",
      "-3" -> "Unknown"
    )

    val drinkerStatusMap = Map(
      "0" -> "Never",
      "1" -> "Previous",
      "2" -> "Current",
      "-3" -> "Unknown"
    )

    val waistHipRatio = (getDecimal(WAIST_CIRCUMFERENCE), getDecimal(HIP_CIRCUMFERENCE)) match {
      case (w, h) if w.nonEmpty && h.nonEmpty => Some(w.get/h.get)
      case _ => None
    }

    val dentalProblemCodes = getCodes(DENTAL_PROBLEMS)

    Map(
      "Age 0.0" -> getDecimal(AGE),
      "BMI 0.0" -> getDecimal(BMI),
      "Weight 0.0" -> getDecimal(WEIGHT),
      "Standing height 0.0" -> getDecimal(HEIGHT),
      "C-reactive protein 0.0" -> getDecimal(CRP),
      "Cholesterol 0.0" -> getDecimal(CHOLESTEROL),
      "Cystatin C 0.0" -> getDecimal(CYSTATIN_C),
      "Diastolic blood pressure 0.0" -> getDecimal(DIASTOLIC_BLOOD_PRESSURE),
//      "Diastolic blood pressure 0.1" -> ,
      "Gamma glutamyltransferase 0.0" -> getDecimal(GAMMA_GLUTAMYLTRANSFERASE),
      "Getting up 0.0_num" -> getCode(GETTING_UP),
      "HbA1c 0.0" -> getDecimal(HBA1C),
      "IMD Employment score 0.0" -> getDecimal(IMD_EMPLOYMENT_SCORE),
      "IMD Health score 0.0" -> getDecimal(IMD_HEALTH_SCORE),
      "LDL direct 0.0" -> getDecimal(LDL),
      "Sleep duration 0.0" -> getDecimal(SLEEP),
      "Systolic blood pressure 0.0" -> getDecimal(SYSTOLIC_BLOOD_PRESSURE),
      "Waist circumference 0.0" -> getDecimal(WAIST_CIRCUMFERENCE),
      "Hip circumference 0.0" -> getDecimal(HIP_CIRCUMFERENCE),
      "Day nap 0.0_ord" -> getCode(NAP),
      "Sleeplessness 0.0_ord" -> getCode(INSOMNIA),
      "Dental problems0 0.0_Dentures" -> matchesAnyCode(dentalProblemCodes, "6"),
      "Mother illnesses1 0.1_None of the above (group 2)" -> getCode(MOTHER_ILLNESS_2).flatMap(matchesCode(_, "-27")),
      "Pain0 0.0_None of the above" -> getCode(PAIN).flatMap(matchesCode(_, "-7")),
      "Pain0 0.0_Pain all over the body" -> getCode(PAIN).flatMap(matchesCode(_, "8")),
      "Sibling illnesses0 0.0_None of the above (group 1)" -> getCode(SIBLING_ILLNESS_1).flatMap(matchesCode(_, "-17")),
      "Body fat % 0.0" -> getDecimal(BODY_FAT_PERCENTAGE),
      "Mother illnesses1 0.1_None of the above (group 1)" -> getCode(MOTHER_ILLNESS_1).flatMap(matchesCode(_, "-17")),
      "High light scatter reticulocyte pct 0.0" -> getDecimal(HIGH_LIGHT_SCATTER_RETICULOCYTE_PCT),
      "Waist-to-hip ratio 0.0" -> waistHipRatio,
      "Dental problems0 0.0_None of the above" -> matchesAnyCode(dentalProblemCodes, "-7"),
      "Accommodation own/rent_recoded" ->
        mapCategoricalCode(
          ACCOMMODATION,
          "Accommodation own/rent_recoded",
          accommodationMap
        ),

      "Coffee type 0.0" ->
        mapCategoricalCode(
          COFFEE_TYPE,
          "Coffee type 0.0",
          coffeeTypeMap
        ),

      "Drinker status 0.0" ->
        mapCategoricalCode(
          ALCOHOL_DRINKER_STATUS,
          "Drinker status 0.0",
          drinkerStatusMap
        ),

      "Employment_recoded" ->
        mapCategoricalCode(
          EMPLOYMENT,
          "Employment_recoded",
          employmentMap
        ),

      "Fed-up feelings 0.0" ->
        mapCategoricalCode(
          FED_UP_FEELINGS,
          "Fed-up feelings 0.0",
          mentalYesNoMap
        ),

      "Fractured bones 0.0" ->
        mapCategoricalCode(
          FRACTURED_BONES,
          "Fractured bones 0.0",
          fracturedBonesMap
        ),

      "Hearing difficulty 0.0" ->
        mapCategoricalCode(
          HEARING_DIFFICULTY,
          "Hearing difficulty 0.0",
          hearingDifficultyMap
        ),

      "Loneliness 0.0" ->
        mapCategoricalCode(
          LONELINESS,
          "Loneliness 0.0",
          mentalYesNoMap
        ),

      "Miserableness 0.0" ->
        mapCategoricalCode(
          MISERABLENESS,
          "Miserableness 0.0",
          mentalYesNoMap
        ),

      "Mood swings 0.0" ->
        mapCategoricalCode(
          MOOD_SWING,
          "Mood swings 0.0",
          mentalYesNoMap
        ),

      "Nerves 0.0" ->
        mapCategoricalCode(
          NERVES,
          "Nerves 0.0",
          mentalYesNoMap
        ),

      "Qualification_recoded" ->
        mapCategoricalCode(
          QUALIFICATION,
          "Qualification_recoded",
          qualificationMap
        ),

      "Sensitivity 0.0" ->
        mapCategoricalCode(
          SENSITIVITY,
          "Sensitivity 0.0",
          mentalYesNoMap
        ),

      "Smoking status 0.0" ->
        mapCategoricalCode(
          SMOKING_STATUS,
          "Smoking status 0.0",
          smokingStatusMap
        ),

      "Tobacco smoking 0.0" ->
        mapCategoricalCode(
          TOBACCO_SMOKING,
          "Tobacco smoking 0.0",
          tobaccoSmokingMap
        ),

      "Worrier feelings 0.0" ->
        mapCategoricalCode(
          WORRIER_FEELINGS,
          "Worrier feelings 0.0",
          mentalYesNoMap
        ),


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


