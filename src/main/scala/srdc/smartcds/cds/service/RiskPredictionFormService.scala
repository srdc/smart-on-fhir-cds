package srdc.smartcds.cds.service

import akka.actor.ActorSystem
import io.onfhir.cds.api.model.CdsResponse
import io.onfhir.cds.service.{BaseCdsService, CdsServiceContext, CdsServiceRequest}
import org.json4s.DefaultFormats
import srdc.smartcds.cds.flow.{RiskPredictionFlowExecution, RiskPredictionFormFlowExecution}
import srdc.smartcds.model.fhir.{Condition, FamilyMemberHistory, Observation, Patient, Questionnaire, QuestionnaireResponse}
import srdc.smartcds.util.RiskPredictionUtil

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

/**
 * Minimal CDS Service - just routes to flow logic
 */
class RiskPredictionFormService(cdsServiceContext: CdsServiceContext)
  extends BaseCdsService(cdsServiceContext) {

  implicit val formats: DefaultFormats.type = DefaultFormats
  implicit val system: ActorSystem = ActorSystem("RiskPredictionService")

  override def executeCds(
                           cdsServiceRequest: CdsServiceRequest
                         )(implicit ex: ExecutionContext): Future[CdsResponse] = {

    val responseBuilder = createResponse(cdsServiceRequest)
    val patientId = cdsServiceRequest.getStringContext("patientId")

    val patient = cdsServiceRequest.getReadPrefetch("patient").extract[Patient]
    val questionnaire = cdsServiceRequest.getSearchPrefetch("questionnaire").map(_.extract[Questionnaire])

    val phq2 = cdsServiceRequest.getSearchPrefetch("phq2").headOption.map(_.extract[QuestionnaireResponse])
    val who5 = cdsServiceRequest.getSearchPrefetch("who5").headOption.map(_.extract[QuestionnaireResponse])
    val hdi4 = cdsServiceRequest.getSearchPrefetch("hdi4").headOption.map(_.extract[QuestionnaireResponse])
    val share = cdsServiceRequest.getSearchPrefetch("share").headOption.map(_.extract[QuestionnaireResponse])

    val bmi = cdsServiceRequest.getSearchPrefetch("bmi").headOption.map(_.extract[Observation])
    val weight = cdsServiceRequest.getSearchPrefetch("weight").headOption.map(_.extract[Observation])
    val height = cdsServiceRequest.getSearchPrefetch("height").headOption.map(_.extract[Observation])
    val dbp = cdsServiceRequest.getSearchPrefetch("diastolic_blood_pressure").headOption.map(_.extract[Observation])
    val sbp = cdsServiceRequest.getSearchPrefetch("systolic_blood_pressure").headOption.map(_.extract[Observation])
    val bp = cdsServiceRequest.getSearchPrefetch("blood_pressure_panel").headOption.map(_.extract[Observation])
    val waistCircumference = cdsServiceRequest.getSearchPrefetch("waist_circumference").headOption.map(_.extract[Observation])
    val hipCircumference = cdsServiceRequest.getSearchPrefetch("hip_circumference").headOption.map(_.extract[Observation])
    val bodyFat = cdsServiceRequest.getSearchPrefetch("body_fat").headOption.map(_.extract[Observation])
    val crp = cdsServiceRequest.getSearchPrefetch("crp").headOption.map(_.extract[Observation])
    val cholesterol = cdsServiceRequest.getSearchPrefetch("cholesterol").headOption.map(_.extract[Observation])
    val cystatinC = cdsServiceRequest.getSearchPrefetch("cystatin_c").headOption.map(_.extract[Observation])
    val gammaGT = cdsServiceRequest.getSearchPrefetch("gamma_gt").headOption.map(_.extract[Observation])
    val hba1c = cdsServiceRequest.getSearchPrefetch("hba1c").headOption.map(_.extract[Observation])
    val ldl = cdsServiceRequest.getSearchPrefetch("ldl").headOption.map(_.extract[Observation])
    val sleepDuration = cdsServiceRequest.getSearchPrefetch("sleep_duration").headOption.map(_.extract[Observation])
    val routineEvents = cdsServiceRequest.getSearchPrefetch("routine_events").headOption.map(_.extract[Observation])
    val smokingStatus = cdsServiceRequest.getSearchPrefetch("smoking_status").headOption.map(_.extract[Observation])
    val alcoholIntake = cdsServiceRequest.getSearchPrefetch("alcohol_intake").headOption.map(_.extract[Observation])
    val mood = cdsServiceRequest.getSearchPrefetch("mood").map(_.extract[Observation])

    val mouthUlcer = cdsServiceRequest.getSearchPrefetch("mouth_ulcer").nonEmpty
    val gumPain = cdsServiceRequest.getSearchPrefetch("gum_pain").nonEmpty
    val bleedingGums = cdsServiceRequest.getSearchPrefetch("bleeding_gums").nonEmpty
    val looseTeeth = cdsServiceRequest.getSearchPrefetch("loose_teeth").nonEmpty
    val toothache = cdsServiceRequest.getSearchPrefetch("toothache").nonEmpty
    val dentures = cdsServiceRequest.getSearchPrefetch("dentures").nonEmpty
    val fracturedBones = cdsServiceRequest.getSearchPrefetch("fractured_bones").headOption.map(_.extract[Condition])
    val hearingLoss = cdsServiceRequest.getSearchPrefetch("hearing_loss").nonEmpty
    val deafness = cdsServiceRequest.getSearchPrefetch("deafness").nonEmpty
    val pain = cdsServiceRequest.getSearchPrefetch("pain").nonEmpty
    val painAllOverBody = cdsServiceRequest.getSearchPrefetch("pain_all_over_body").nonEmpty

    val motherIllnesses1 = cdsServiceRequest.getSearchPrefetch("mother_illnesses_1").nonEmpty
    val motherIllnesses2 = cdsServiceRequest.getSearchPrefetch("mother_illnesses_2").nonEmpty
    val siblingIllnesses1 = cdsServiceRequest.getSearchPrefetch("sibling_illnesses_1").nonEmpty

    RiskPredictionFormFlowExecution.execute(cdsServiceRequest, responseBuilder, questionnaire, patient, phq2, who5, hdi4, share,
      bmi, weight, height, dbp, sbp, bp, waistCircumference, hipCircumference, bodyFat, crp, cholesterol, cystatinC, gammaGT,
      hba1c, ldl, sleepDuration, routineEvents, smokingStatus, alcoholIntake, mood, mouthUlcer, gumPain, bleedingGums,
      looseTeeth, toothache, dentures, fracturedBones, hearingLoss, deafness, pain, painAllOverBody, motherIllnesses1,
      motherIllnesses2, siblingIllnesses1)

  }
}