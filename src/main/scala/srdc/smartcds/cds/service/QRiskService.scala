package srdc.smartcds.cds.service

import srdc.smartcds.cds.flow.QRiskFlowExecution
import io.onfhir.cds.api.model.CdsResponse
import io.onfhir.cds.service.{BaseCdsService, CdsServiceContext, CdsServiceRequest}
import srdc.smartcds.model.fhir.{Condition, FamilyMemberHistory, Goal, MedicationStatement, Observation, Patient}
import org.json4s.{DefaultFormats, JNothing}
import srdc.smartcds.util.{CdsPrefetchUtil, ConceptIdUtil}

import scala.concurrent.{ExecutionContext, Future}

class QRiskService(cdsServiceContext: CdsServiceContext) extends SHCBaseService(cdsServiceContext) {
  implicit val formats: DefaultFormats.type = DefaultFormats
  override def serviceId: String = "qrisk"

  override def executeCdsWithParsedSHCRequest(cdsServiceRequest: CdsServiceRequest)(implicit ex: ExecutionContext): Future[CdsResponse] = {
    val fhirPathEvaluator = getFhirPathEvaluator(cdsServiceRequest)
    val responseBuilder = createResponse(cdsServiceRequest)

    Future {
      QRiskFlowExecution.executeFlow(
        age = CdsPrefetchUtil.getAge(fhirPathEvaluator),
        gender = fhirPathEvaluator.evaluateString(CdsPrefetchUtil.GENDER_PATH, JNothing).head,
        atrialFibrillation = CdsPrefetchUtil.existsInt("AtrialFibrillation", fhirPathEvaluator),
        rheumatoidArthritis = CdsPrefetchUtil.existsInt("RheumatoidArthritis", fhirPathEvaluator),
        ckd45 = CdsPrefetchUtil.existsInt("CKD4_5", fhirPathEvaluator),
        type1Diabetes = CdsPrefetchUtil.existsInt("Type1Diabetes", fhirPathEvaluator),
        type2Diabetes = CdsPrefetchUtil.existsInt("Type2Diabetes", fhirPathEvaluator),
        hypertensiveTreatment = CdsPrefetchUtil.existsInt("HypertensiveTreatment", fhirPathEvaluator),
        bmi = CdsPrefetchUtil.getObservationValue("BMI", fhirPathEvaluator),
        totalCholesterol = CdsPrefetchUtil.getObservationValue("TotalCholesterol", fhirPathEvaluator),
        hdl = CdsPrefetchUtil.getObservationValue("HDL", fhirPathEvaluator),
        bp = CdsPrefetchUtil.getObservationOrComponentValue("BP_SBP", ConceptIdUtil.SYSTOLIC_BP, fhirPathEvaluator),
        smokingStatus = CdsPrefetchUtil.getSmokingCategory("SmokingStatus", fhirPathEvaluator),
        cvdFmh = CdsPrefetchUtil.existsInt("CVD_FMH", fhirPathEvaluator),
        cvd = CdsPrefetchUtil.exists("CVD", fhirPathEvaluator),
        atorvastatin = CdsPrefetchUtil.exists("Atorvastatin", fhirPathEvaluator),
        responseBuilder
      ).cdsResponse
    }
  }
}
