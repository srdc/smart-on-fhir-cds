package srdc.smartcds.util

import io.onfhir.cds.config.CdsConfig.formats
import io.onfhir.path.FhirPathEvaluator
import org.json4s.{JNothing, JValue}

import java.time.{LocalDate, Period, ZonedDateTime}

/**
 * Helper utilities to obtain required values from the CDS prefetch
 */
object CdsPrefetchUtil {
  /** FHIR path of patient's birth date */
  final val DATE_OF_BIRTH_PATH = "%cdsPrefetch.patient.birthDate"
  /** FHIR path of patient's gender */
  final val GENDER_PATH = "%cdsPrefetch.patient.gender"

  /** FHIR Paths for specific fields */
  private def conditionOnSetDateTimePath(prefetchKey: String)  = s"%cdsPrefetch.$prefetchKey.onsetDateTime"
  private def observationValuePath(prefetchKey: String) = s"%cdsPrefetch.$prefetchKey.valueQuantity.value.first()"
  private def existsPath(prefetchKey: String) = s"%cdsPrefetch.$prefetchKey.exists()"
  private def observationOrComponentValuePath(prefetchKey: String, conceptId: String): String = {
    val codes = ValueSetUtil.getConceptSystemAndCode(conceptId)
    val codesStr = codes.map(coding => s"'${coding._1}|${coding._2}'").mkString(",")
    s"%cdsPrefetch.$prefetchKey.where($$this.code.coding.isCodingIn($codesStr)).valueQuantity.value || " +
      s"%cdsPrefetch.$prefetchKey.component.where($$this.code.coding.isCodingIn($codesStr)).valueQuantity.value"
  }

  /**
   * Checks if any resource for the given prefetch exists
   * @param prefetchKey
   * @param fhirPathEvaluator
   * @return Boolean
   */
  def exists(prefetchKey: String, fhirPathEvaluator: FhirPathEvaluator): Boolean = {
    fhirPathEvaluator.satisfies(CdsPrefetchUtil.existsPath(prefetchKey), JNothing)
  }

  /**
   * Checks if any resource for the given prefetch exists and returns 1 or 0
   * @param prefetchKey
   * @param fhirPathEvaluator
   * @return Int
   */
  def existsInt(prefetchKey: String, fhirPathEvaluator: FhirPathEvaluator): Int = {
    if (exists(prefetchKey, fhirPathEvaluator)) 1 else 0
  }

  /**
   * Gets valueQuantity.value for given prefetch
   * @param prefetchKey
   * @param fhirPathEvaluator
   * @return
   */
  def getObservationValue(prefetchKey: String, fhirPathEvaluator: FhirPathEvaluator): Option[Double] = {
    fhirPathEvaluator.evaluateOptionalNumerical(CdsPrefetchUtil.observationValuePath(prefetchKey), JNothing).map(_.toDouble)
  }

  /**
   * Gets valueQuantity.value or component.valueQuantity.value for given concept in the given prefetch
   * @param prefetchKey Prefetch key
   * @param conceptId Concept ID to lookup the codes
   * @param fhirPathEvaluator
   * @return
   */
  def getObservationOrComponentValue(prefetchKey: String, conceptId: String, fhirPathEvaluator: FhirPathEvaluator): Option[Double] = {
    CdsPrefetchUtil.getObservationOrComponentSeqValue(prefetchKey, conceptId, fhirPathEvaluator).headOption
  }

  /**
   * Gets valueQuantity.value or component.valueQuantity.value for given concept in the given prefetch
   * @param prefetchKey Prefetch key
   * @param conceptId Concept ID to lookup the codes
   * @param fhirPathEvaluator
   * @return
   */
  def getObservationOrComponentSeqValue(prefetchKey: String, conceptId: String, fhirPathEvaluator: FhirPathEvaluator): Seq[Double] = {
    fhirPathEvaluator.evaluateNumerical(CdsPrefetchUtil.observationOrComponentValuePath(prefetchKey, conceptId), JNothing).map(_.toDouble)
  }

  /**
   * Determines the smoking category using given prefetch
   * @param prefetchKey
   * @param fhirPathEvaluator
   * @return Smoking Category in scale of 0 to 4 (0-Never Smoker, 1-Former Smoker, 2-Light Smoker, 3-Every day smoker, 4-Heavy Smoker)
   */
  def getSmokingCategory(prefetchKey: String, fhirPathEvaluator: FhirPathEvaluator): Option[Int] = {
    fhirPathEvaluator.evaluateString(s"%cdsPrefetch.$prefetchKey.valueCodeableConcept.coding.code", JNothing).headOption match {
      case Some("LA18978-9"|"LA18980-5"|"266919005") => Some(SmokingCategoryEnum.NEVER)
      case Some("LA15920-4"|"8517006") => Some(SmokingCategoryEnum.FORMER)
      case Some("LA18977-1"|"LA18982-1") => Some(SmokingCategoryEnum.LIGHT)
      case Some("LA18979-7"|"LA18976-3"|"449868002") => Some(SmokingCategoryEnum.DAILY)
      case Some("LA18981-3") => Some(SmokingCategoryEnum.HEAVY)
      case _ => None
    }
  }

  /**
   * Determines the ethnicity of the patient (USED IN QRISK3)
   * @param prefetchKey
   * @param fhirPathEvaluator
   * @return Ethnicity Category in scale of 0 to 8 (0-White or not stated, 1-Indian, 2-Pakistani, 3-Bangladeshi, 4-Other Asian, 5-Black Caribbean, 6-Black African, 7-Chinese, 8-Other ethnic group)
   */
  def getEthnicityCategory(prefetchKey: String, fhirPathEvaluator: FhirPathEvaluator): Option[Int] = {
    fhirPathEvaluator.evaluateOptionalString(s"%cdsPrefetch.$prefetchKey.valueCodeableConcept.coding.code", JNothing) match {
      case Some("0") => Some(0)
      case Some("1") => Some(1)
      case Some("2") => Some(2)
      case Some("3") => Some(3)
      case Some("4") => Some(4)
      case Some("5") => Some(5)
      case Some("6") => Some(6)
      case Some("7") => Some(7)
      case Some("8") => Some(8)
      case _ => None
    }
  }

  /**
   * Determines the race using given prefetch
   *
   * @param prefetchKey
   * @param fhirPathEvaluator
   * @return race of the patient
   */
  def getRaceCategory(prefetchKey: String, fhirPathEvaluator: FhirPathEvaluator): String = {
    /*
      This function is specifically for ACC/AHA algorithm since it has only parameters for black
      people and white people. Any race other than black (asians, indians etc...)
      are considered "white" for this algorithm.
    */
    fhirPathEvaluator.evaluateOptionalString(s"%cdsPrefetch.$prefetchKey.valueCodeableConcept.coding.code", JNothing) match {
      case Some("LA6162-7") => "africanamerican"
      case _ => "white"
    }
  }

  /**
   * Calculates the age of the patient
   * @param fhirPathEvaluator
   * @return
   */
  def getAge(fhirPathEvaluator: FhirPathEvaluator): Int = {
    val dob = fhirPathEvaluator.evaluateDateTime(CdsPrefetchUtil.DATE_OF_BIRTH_PATH, JNothing).head.asInstanceOf[LocalDate]
    Period.between(dob, LocalDate.now()).getYears
  }

  def getConditionDuration(prefetchKey: String, fhirPathEvaluator: FhirPathEvaluator): Option[Int] = {
    val dob = fhirPathEvaluator.evaluateOptionalDateTime(CdsPrefetchUtil.conditionOnSetDateTimePath(prefetchKey), JNothing)
    if(dob.isEmpty) {
      return None
    } else {
      val localDate = dob.get.asInstanceOf[ZonedDateTime].toLocalDate
      Option(Period.between(localDate, LocalDate.now()).getYears)
    }
  }

  /**
   * Parses url into ResourceType, ID and search parameters
   * @param url
   * @return
   */
  final def parseUrlSegment(url: String): (String, Option[String], Option[Seq[(String, List[String])]]) = {
    val readRegex = """^([^/?]+)/([^/?]+)$""".r
    val searchRegex = """^([^/?]+)\?(.*)$""".r
    url match {
      case readRegex(resourceType, id) =>
        (resourceType, Some(id), None) // Read operation (e.g., "Patient/123")
      case searchRegex(resourceType, queryString) =>
        val queryPairs = queryString.split("&").flatMap { param =>
          param.split("=") match {
            case Array(key, value) => Some(key -> List(value))
            case _ => None
          }
        }
        (resourceType, None, Some(queryPairs)) // Search operation (e.g., "Condition?code=123")
      case _ =>
        (null, None, None)
    }
  }

  /**
   * Replaces context parameters in the given tuple
   * @param param
   * @param contextParams
   * @return
   */
  final def replaceContextParams(param: (String, List[String]), contextParams: Map[String, JValue]): (String, List[String]) = {
    (param._1 -> param._2.map(value => {
      var _value = value
      for (contextParam <- contextParams) {
        if (_value.contains(s"{{context.${contextParam._1}}}")) {
          _value = _value.replaceAll(s"\\{\\{context.${contextParam._1}}}", contextParam._2.extract[String])
        }
      }
      _value
    }))
  }

}
