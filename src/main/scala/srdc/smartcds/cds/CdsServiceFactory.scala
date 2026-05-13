package srdc.smartcds.cds

import srdc.smartcds.cds.service.{ACCAHAService, AdvanceService, AskLlmService, DefinitionService, InMemorySearchService, QRisk3Service, QRiskService, RiskPredictionFormService, RiskPredictionService, Score2Service, ShapExplainService, SmartRiskService, ValueSetService}
import io.onfhir.cds.service.{CdsServiceContext, ICdsService, ICdsServiceFactory}

object CdsServiceFactory extends ICdsServiceFactory {

  /**
   * Service IDs implemented in the project
   */
  val servicesSupported: Set[String] = Set("qrisk", "definition", "score2", "smart", "qrisk3", "acc_aha", "advance",
    "valueset", "in-memory-search", "risk_prediction", "shap_explain", "ask_llm", "risk_prediction_form")

  /**
   * Checks if there is an implemented service with the given service ID
   * @param serviceId CDS Service ID
   * @return
   */
  override def isServiceSupported(serviceId: String): Boolean = {
    servicesSupported.contains(serviceId)
  }

  /**
   * Creates Service instant with the given context and service ID
   * @param cdsServiceContext Context of the CDS request to be passed on the services
   * @return
   */
  override def createCdsService(cdsServiceContext: CdsServiceContext): ICdsService = {
    cdsServiceContext.serviceId match {
      case "qrisk" => new QRiskService(cdsServiceContext)
      case "definition" => new DefinitionService(cdsServiceContext)
      case "score2" => new Score2Service(cdsServiceContext)
      case "advance" => new AdvanceService(cdsServiceContext)
      case "smart" => new SmartRiskService(cdsServiceContext)
      case "qrisk3" => new QRisk3Service(cdsServiceContext)
      case "acc_aha" => new ACCAHAService(cdsServiceContext)
      case "valueset" => new ValueSetService(cdsServiceContext)
      case "in-memory-search" => new InMemorySearchService(cdsServiceContext)
      case "risk_prediction" => new RiskPredictionService(cdsServiceContext)
      case "risk_prediction_form" => new RiskPredictionFormService(cdsServiceContext)
      case "shap_explain" => new ShapExplainService(cdsServiceContext)
      case "ask_llm" => new AskLlmService(cdsServiceContext)
      case other => throw new Exception(s"Service $other not supported!")
    }
  }
}
