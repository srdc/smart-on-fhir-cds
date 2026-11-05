package srdc.smartcds.cds.service

import io.onfhir.cds.api.model.CdsResponse
import io.onfhir.cds.service.{BaseCdsService, CdsServiceContext, CdsServiceRequest}
import org.json4s.JsonAST.{JObject, JString}
import org.json4s.jackson.Serialization.write
import org.json4s.{DefaultFormats, JValue}

import scala.concurrent.{ExecutionContext, Future}

/**
 * CDS Service listing the diseases a risk prediction can be requested for, so that the user can choose them
 * before running risk_prediction (whose optional 'diseases' context then limits the ML service to these).
 *
 * TODO: the list should come from the ML service's info endpoint (shap-client). The STAGE model does not
 * have that endpoint yet, so the list below is hard-coded: the diseases deployed in the SRDC PoC strata of
 * stage_code_2 (shap-client/data/models/stage2), excluding the ones shap-client does not deploy
 * (DEPLOY_EXCLUDED_DISEASES). Names are the ones shap-client uses (it also accepts '_' instead of spaces).
 * Sex-specific cancers are offered only for the matching patient gender; a disease that has no model in the
 * patient's stratum (e.g. Osteoporosis for men under 60) is simply not returned by the ML service.
 *
 * Response: one card whose 'detail' is a JSON document {"source": ..., "diseases": [{"id", "name", "group"}]}.
 */
class RiskPredictionDiseasesService(cdsServiceContext: CdsServiceContext) extends BaseCdsService(cdsServiceContext) {
  implicit val formats: DefaultFormats.type = DefaultFormats

  override def executeCds(cdsServiceRequest: CdsServiceRequest)(implicit ex: ExecutionContext): Future[CdsResponse] = {
    val responseBuilder = createResponse(cdsServiceRequest)
    Future {
      val gender = patientGender(cdsServiceRequest)
      val diseases =
        RiskPredictionDiseasesService.DISEASES
          .filter(d => d.sex.isEmpty || gender.isEmpty || d.sex == gender)
          .map(d => Map("id" -> d.name, "name" -> d.name, "group" -> d.group))
      val detail = write(Map("source" -> "hard-coded list (ML service info endpoint not available yet)", "diseases" -> diseases))
      responseBuilder
        .withCard(_.loadCardWithPostTranslation("card-risk-prediction-diseases", "diseasesJson" -> detail))
        .cdsResponse
    }
  }

  /** 'male' / 'female' from the patient prefetch (a single resource or a list), if available */
  private def patientGender(cdsServiceRequest: CdsServiceRequest): Option[String] = {
    val patient: Option[JValue] = cdsServiceRequest.prefetches.get("patient") match {
      case Some(list: List[_]) => list.headOption.collect { case p: JObject => p }
      case Some(p: JObject) => Some(p)
      case _ => None
    }
    patient.flatMap(p => (p \ "gender") match {
      case JString(g) => Some(g.toLowerCase)
      case _ => None
    })
  }
}

object RiskPredictionDiseasesService {
  case class Disease(name: String, group: String, sex: Option[String] = None)

  final val DISEASES: Seq[Disease] = Seq(
    Disease("Atrial Fibrillation", "Cardiovascular"),
    Disease("Coronary Heart Disease", "Cardiovascular"),
    Disease("Heart Failure", "Cardiovascular"),
    Disease("Hypertension", "Cardiovascular"),
    Disease("Peripheral Vascular Disease", "Cardiovascular"),
    Disease("Stroke Cerebrovascular", "Cardiovascular"),
    Disease("Diabetes Mellitus", "Metabolic"),
    Disease("Hyperlipidemia", "Metabolic"),
    Disease("Hypothyroidism", "Metabolic"),
    Disease("Chronic Kidney Disease", "Kidney and liver"),
    Disease("Chronic Liver Disease", "Kidney and liver"),
    Disease("COPD", "Respiratory"),
    Disease("Sleep Apnea", "Respiratory"),
    Disease("Osteoarthritis", "Musculoskeletal"),
    Disease("Osteoporosis", "Musculoskeletal"),
    Disease("Rheumatoid Arthritis", "Musculoskeletal"),
    Disease("Anxiety", "Mental health"),
    Disease("Depression", "Mental health"),
    Disease("Substance Use Disorder", "Mental health"),
    Disease("Cancer Breast", "Cancer", Some("female")),
    Disease("Cancer Colorectal", "Cancer"),
    Disease("Cancer Lung", "Cancer"),
    Disease("Cancer Prostate", "Cancer", Some("male")),
    Disease("Cancer Other", "Cancer")
  )
}
