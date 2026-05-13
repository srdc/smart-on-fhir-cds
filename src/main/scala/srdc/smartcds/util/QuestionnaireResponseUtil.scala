package srdc.smartcds.util

import srdc.smartcds.model.fhir._
import scala.jdk.CollectionConverters._
import com.typesafe.config.ConfigFactory

object QuestionnaireResponseUtil {

  private lazy val config = ConfigFactory.load()

  /**
   * Find latest observation by UK Biobank code
   */
  def findObservation(ukbCode: String, observations: Seq[Observation]): Option[Observation] = {
    val systemCode = getSystemCode(ukbCode)
    if (systemCode.isEmpty) return None
    FhirParseHelper.findLatestObservation(List(systemCode.get), observations)
  }

  /**
   * Get code from config
   */
  private def getSystemCode(ukbCode: String): Option[(String, String)] = {
    try {
      val configPath = s"""uk-biobank-direct-mapping."$ukbCode""""
      if (config.hasPath(configPath)) {
        val value = config.getString(configPath)
        val parts = value.split("\\|")
        if (parts.length == 2) {
          Some((parts(0), parts(1)))
        } else {
          None
        }
      } else {
        None
      }
    } catch {
      case _: Exception => None
    }
  }

  /**
   * Generate pre-filled QuestionnaireResponse from observations
   */
  def generatePrefilledResponse(
                                 questionnaireUrl: String,
                                 patientId: String,
                                 observations: Seq[Observation]
                               ): QuestionnaireResponse = {

    val items = generateItems(observations)

    QuestionnaireResponse(
      resourceType = "QuestionnaireResponse",
      identifier = None,
      meta = None,
      contained = None,
      questionnaire = questionnaireUrl,
      status = "in-progress",
      item = if (items.nonEmpty) Some(items.toArray) else None
    )
  }

  /**
   * Generate items from observations
   */
  private def generateItems(observations: Seq[Observation]): Seq[Item] = {
    val ukbCodes = getUKBCodes()

    ukbCodes.flatMap { ukbCode =>
      val obs = findObservation(ukbCode, observations)
      obs.flatMap(createItem(ukbCode, _))
    }
  }

  /**
   * - linkId = UK Biobank code directly (not "ukb-{code}")
   * - Answer includes BOTH valueCoding AND actual value (valueDecimal/valueInteger)
   */
  private def createItem(ukbCode: String, observation: Observation): Option[Item] = {
    val answer = createAnswer(ukbCode, observation)

    if (answer.isEmpty) return None

    val itemType = getItemType(observation)
    val displayText = getDisplayText(ukbCode)

    Some(Item(
      linkId = ukbCode,  // UK Biobank code as linkId
      prefix = None,
      text = Some(displayText),
      `type` = Some(itemType),
      required = Some(false),
      item = None,
      answerOption = None,
      answer = Some(Array(answer.get)),
      code = None
    ))
  }

  /**
   * Create answer
   */
  private def createAnswer(ukbCode: String, observation: Observation): Option[Answer] = {
    val ukbCoding = Coding(
      system = Some("http://ukbiobank.ac.uk"),
      code = ukbCode,
      display = Some(getDisplayText(ukbCode)),
    )

    // valueQuantity -> Include BOTH valueDecimal AND valueInteger
    if (observation.valueQuantity.isDefined && observation.valueQuantity.get.value.isDefined) {
      val actualValue = observation.valueQuantity.get.value.get
      return Some(Answer(
        valueCoding = Some(ukbCoding),           // UK Biobank code
        valueInteger = Some(actualValue.round.toInt),  // Rounded for integer fields
      ))
    }


    // valueBoolean -> Convert to integer (1/0)
    if (observation.valueBoolean.isDefined) {
      return Some(Answer(
        valueCoding = Some(ukbCoding),
        valueInteger = Some(if (observation.valueBoolean.get) 1 else 0)
      ))
    }

    None
  }

  /**
   * Get item type from observation
   */
  private def getItemType(observation: Observation): String = {
    if (observation.valueQuantity.isDefined) "decimal"  // FIXED: Use decimal, not integer
    else if (observation.valueInteger.isDefined) "integer"
    else if (observation.valueBoolean.isDefined) "boolean"
    else if (observation.valueCodeableConcept.isDefined) "choice"
    else "string"
  }

  /**
   * Get all UK Biobank codes from config
   */
  private def getUKBCodes(): Seq[String] = {
    try {
      val mappingConfig = config.getConfig("uk-biobank-direct-mapping")
      mappingConfig.entrySet().asScala.map(_.getKey.replace("\"", "")).toSeq
    } catch {
      case _: Exception => Seq.empty
    }
  }

  /**
   * Get display text for UK Biobank code
   */
  private def getDisplayText(ukbCode: String): String = {
    try {
      val configPath = s"""uk-biobank-display-texts."$ukbCode""""
      if (config.hasPath(configPath)) {
        config.getString(configPath)
      } else {
        s"UK Biobank Field $ukbCode"
      }
    } catch {
      case _: Exception => s"UK Biobank Field $ukbCode"
    }
  }
}