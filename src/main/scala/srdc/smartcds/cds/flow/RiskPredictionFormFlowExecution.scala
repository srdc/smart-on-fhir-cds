package srdc.smartcds.cds.flow

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import io.onfhir.cds.api.model.CdsResponse
import io.onfhir.cds.model.{CdsException, CdsResponseBuilder}
import io.onfhir.cds.service.CdsServiceRequest
import org.json4s.DefaultFormats
import org.json4s.JsonAST.JObject
import org.json4s.jackson.Serialization.write
import srdc.smartcds.cds.client.MLModelClient
import srdc.smartcds.config.SmartCdsConfig
import srdc.smartcds.model.UKBiobankFeatures
import srdc.smartcds.model.fhir.{Answer, Condition, Identifier, Item, Observation, ObservationComponent, Patient, Questionnaire, QuestionnaireResponse}
import srdc.smartcds.util.{DateTimeUtil, FhirObservationBuilder, QuestionnaireResponseUtil}

import srdc.smartcds.util.Json4sFormatter._
import java.time.{LocalDate, LocalDateTime, Period}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

object RiskPredictionFormFlowExecution {

  implicit val formats: DefaultFormats.type = DefaultFormats

  private def getAnswersCodes(qr: QuestionnaireResponse, linkId: String): Option[Array[String]] = {
    qr.item.getOrElse(Array.empty).find(item => item.linkId == linkId).flatMap(_.answer map { answers =>
      answers.flatMap(_.valueCoding.map(_.code))
    })
  }

  private def observationValue(obs: Observation): Option[Double] = obs.valueQuantity.flatMap(_.value)

  def execute(cdsServiceRequest: CdsServiceRequest, responseBuilder: CdsResponseBuilder, questionnaire: Seq[Questionnaire],
              patient: Patient, phq2: Option[QuestionnaireResponse], who5: Option[QuestionnaireResponse],
              hdi4: Option[QuestionnaireResponse], share: Option[QuestionnaireResponse], bmi: Option[Observation],
              weight: Option[Observation], height: Option[Observation], dbp: Option[Observation], sbp: Option[Observation],
              bp: Option[Observation], waistCircumference: Option[Observation], hipCircumference: Option[Observation],
              bodyFat: Option[Observation], crp: Option[Observation], cholesterol: Option[Observation],
              cystatinC: Option[Observation], gammaGT: Option[Observation], hba1c: Option[Observation],
              ldl: Option[Observation], sleepDuration: Option[Observation], routineEvents: Option[Observation],
              smokingStatus: Option[Observation], alcoholIntake: Option[Observation], mood: Seq[Observation],
              mouthUlcer: Boolean, gumPain: Boolean, bleedingGums: Boolean, looseTeeth: Boolean, toothache: Boolean,
              dentures: Boolean, fracturedBones: Option[Condition], hearingLoss: Boolean,
              deafness: Boolean, pain: Boolean, painAllOverBody: Boolean, motherIllnesses1: Boolean, motherIllnesses2: Boolean,
              siblingIllnesses1: Boolean)(implicit system: ActorSystem, ec: ExecutionContext): Future[CdsResponse] = {
    import UKBiobankFeatures._

    if (questionnaire.isEmpty) throw new CdsException(StatusCodes.NotFound, Some("Cannot find the features questionnaire!"))
    val questionnaireCode = questionnaire.head.code.flatMap(_.headOption.map(coding => Identifier(coding.system, Some(coding.code))))
    val qr = QuestionnaireResponse(
      resourceType = "QuestionnaireResponse",
      identifier = questionnaireCode,
      meta = None, contained = None,
      questionnaire = "Questionnaire" + questionnaire.head.id,
      status = "",
      item = questionnaire.head.item.map(_.map { group =>
        Item(
          linkId = group.linkId,
          text = group.text,
          item = group.item.map(_.map { item =>
            Item(
              linkId = item.linkId,
              text = item.text,
              answer = item.linkId match {
                case AGE =>
                  numericAnswers(item.`type`, patient.birthDate.map(birthDate => Period.between(LocalDate.parse(birthDate), LocalDate.now()).getYears))
                case WORRIER_FEELINGS => codeAnswers(item, phqValue(phq2, "phq2_q2"))
                //      case MOOD_SWING => ...mood.map(observationValue)...
                case FED_UP_FEELINGS => codeAnswers(item, phqValue(phq2, "phq2_q1"))
                case LONELINESS => codeAnswers(item, shareValue(share, "share_q1"))
                case MISERABLENESS => codeAnswers(item, who5MiserablenessValue(who5))
                case BMI => numericAnswers(item.`type`, calculateBMI(bmi, weight, height).map(_.asInstanceOf[Number]))
                case WEIGHT => numericAnswers(item.`type`, weight.flatMap(observationValue).map(_.asInstanceOf[Number]))
                case HEIGHT => numericAnswers(item.`type`, height.flatMap(observationValue).map(_.asInstanceOf[Number]))
                case WAIST_CIRCUMFERENCE => numericAnswers(item.`type`, waistCircumference.flatMap(observationValue).map(_.asInstanceOf[Number]))
                case HIP_CIRCUMFERENCE => numericAnswers(item.`type`, hipCircumference.flatMap(observationValue).map(_.asInstanceOf[Number]))
                case SYSTOLIC_BLOOD_PRESSURE => numericAnswers(item.`type`, sbp.flatMap(observationValue).orElse(getComponentValue(bp, SmartCdsConfig.sbpCodes)).map(_.asInstanceOf[Number]))
                case DIASTOLIC_BLOOD_PRESSURE => numericAnswers(item.`type`, dbp.flatMap(observationValue).orElse(getComponentValue(bp, SmartCdsConfig.dbpCodes)).map(_.asInstanceOf[Number]))
                case BODY_FAT_PERCENTAGE => numericAnswers(item.`type`, bodyFat.flatMap(observationValue).map(_.asInstanceOf[Number]))
                case SLEEP =>
                  val alternative = routineEvents.flatMap(_.component.flatMap(components => {
                    val wakeUpTime = components.find(_.code.coding.exists(_.code == "wake-up-time")).flatMap(_.valueString)
                    val bedtime = components.find(_.code.coding.exists(_.code == "bedtime")).flatMap(_.valueString)
                    Try((LocalDateTime.parse(wakeUpTime.get).getHour - LocalDateTime.parse(bedtime.get).getHour + 24d) % 24).toOption
                  }))
                  numericAnswers(item.`type`, sleepDuration.flatMap(observationValue).orElse(alternative).map(_.asInstanceOf[Number]))
                case GETTING_UP => codeAnswers(item, who5GettingUpValue(who5))
//                case ALCOHOL_DRINKER_STATUS => alcoholIntake.map(matchAlcoholIntakeCode)
//                case SMOKING_STATUS => smokingStatus.map(matchSmokingStatusCode)
//                case MOTHER_ILLNESS_1 =>
//                case MOTHER_ILLNESS_2 =>
//                case SIBLING_ILLNESS_1 =>
                case DENTAL_PROBLEMS =>
                  val codes = Seq(mouthUlcer, gumPain, bleedingGums, looseTeeth, toothache, dentures).zipWithIndex.map({
                    case (true, i) => Some(i + 1)
                    case _ => None
                  }).filter(_.nonEmpty).map(_.get.toString)
                  codeAnswers(item, codes)
                case FRACTURED_BONES =>
                  if (fracturedBones.exists(condition => condition.onsetDateTime.exists(dt => Period.between(LocalDate.parse(dt), LocalDate.now()).getYears <= 5)))
                    codeAnswers(item, Seq("1"))
                  else None
                case HEARING_DIFFICULTY =>
                  if (deafness) {
                    codeAnswers(item, Seq("99"))
                  } else if (hearingLoss) {
                    codeAnswers(item, Seq("1"))
                  } else {
                    None
                  }
//                case PAIN =>
                case CRP => numericAnswers(item.`type`, crp.flatMap(observationValue).map(_.asInstanceOf[Number]))
                case CHOLESTEROL => numericAnswers(item.`type`, cholesterol.flatMap(observationValue).map(_.asInstanceOf[Number]))
                case CYSTATIN_C => numericAnswers(item.`type`, cystatinC.flatMap(observationValue).map(_.asInstanceOf[Number]))
                case GAMMA_GLUTAMYLTRANSFERASE => numericAnswers(item.`type`, gammaGT.flatMap(observationValue).map(_.asInstanceOf[Number]))
                case HBA1C => numericAnswers(item.`type`, hba1c.flatMap(observationValue).map(_.asInstanceOf[Number]))
                case LDL => numericAnswers(item.`type`, ldl.flatMap(observationValue).map(_.asInstanceOf[Number]))
                case _ => None
              }
            )
          })
        )
      })
    )

//    val waistHipRatio = (waistCircumference.flatMap(observationValue), hipCircumference.flatMap(observationValue)) match {
//      case (w, h) if w.nonEmpty && h.nonEmpty => Some(w.get/h.get)
//      case _ => None
//    }

    Future.apply(
      responseBuilder.withCard(
        _.loadCardWithPostTranslation(
          "card-questionnaire-prefill",
          "prefilledQR" -> qr.toJson
        )
      ).cdsResponse
    )

  }

  private def codeAnswers(item: Item, codes: Option[String]): Option[Array[Answer]] = {
    codeAnswers(item, codes.toSeq)
  }

  private def codeAnswers(item: Item, codes: Seq[String]): Option[Array[Answer]] = {
    if (item.`type`.contains("choice") || item.`type`.contains("open-choice")) {
      item.answerOption.map(_.filter(coding => codes.contains(coding.valueCoding.code)).map(answerOption => {
        Answer(valueCoding = Some(answerOption.valueCoding))
      }))
    } else None
  }

  private def numericAnswers(`type`: Option[String], value: Option[Number]): Option[Array[Answer]] = {
    `type` match {
      case Some("integer") =>
        Some(Array(Answer(
          valueInteger = value.map(_.intValue())
        )))
      case Some("decimal") =>
        Some(Array(Answer(
          valueDecimal = value.map(_.doubleValue())
        )))
      case _ => None
    }
  }

  private def matchAlcoholIntakeCode(observation: Observation) = {

  }

  private def matchSmokingStatusCode(observation: Observation) = {

  }

  private def getComponentValue(obs: Option[Observation], codes: Option[java.util.List[String]]) = {
    obs.flatMap(_.component.flatMap(_.find(_.code.coding.exists(coding => {
      codes.exists(_.contains(coding.code)) || coding.system.exists(system => codes.exists(_.contains(system + "|" + coding.code)))
    })).flatMap(_.valueQuantity.flatMap(_.value))))
  }

  private def calculateBMI(bmi: Option[Observation], weight: Option[Observation], height: Option[Observation]) = {
    if (bmi.nonEmpty) {
      observationValue(bmi.get)
    } else if (weight.nonEmpty && height.nonEmpty) {
      (observationValue(weight.get), observationValue(height.get)) match {
        case (w, h) if w.nonEmpty && h.nonEmpty =>
          val heightUnit =
            height.get.valueQuantity.flatMap(_.unit).getOrElse("")

          val heightInMeters =
            if (heightUnit == "cm") h.get / 100.0
            else h.get

          Some(w.get / Math.pow(heightInMeters, 2))
        case _ => None
      }
    } else None
  }

  private def phqValue(phq2: Option[QuestionnaireResponse], qId: String) = {
    if (phq2.isEmpty) None
    else
      getAnswersCodes(phq2.get, qId) match {
        case Some(answers) => answers.headOption match {
          case Some("A1"|"A2"|"A3") => Some("1")
          case Some("A0") => Some("2")
          case _ => None
        }
        case _ => None
      }
  }

  private def who5GettingUpValue(who5: Option[QuestionnaireResponse]) = {
    if (who5.isEmpty) None
    else
      getAnswersCodes(who5.get, "who5_q4") match {
        case Some(answers) => answers.headOption match {
          case Some("A0") => Some("1")
          case Some("A1"|"A2") => Some("2")
          case Some("A3") => Some("3")
          case Some("A4"|"A5") => Some("4")
          case _ => None
        }
        case _ => None
      }
  }

  private def who5MiserablenessValue(who5: Option[QuestionnaireResponse]) = {
    if (who5.isEmpty) None
    else
      getAnswersCodes(who5.get, "who5_q1") match {
        case Some(answers) => answers.headOption match {
          case Some("A0"|"A1"|"A2") => Some("1")
          case Some("A3"|"A4"|"A5") => Some("2")
          case _ => None
        }
        case _ => None
      }
  }

  private def shareValue(share: Option[QuestionnaireResponse], qId: String) = {
    if (share.isEmpty) None
    else
      getAnswersCodes(share.get, qId) match {
        case Some(answers) => answers.headOption match {
          case Some("A0"|"A1") => Some("1")
          case Some("A2") => Some("2")
          case _ => None
        }
        case _ => None
      }
  }

}