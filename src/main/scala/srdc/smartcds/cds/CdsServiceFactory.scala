package srdc.smartcds.cds

import srdc.smartcds.cds.service.{ACCAHAService, AdvanceService, DefinitionService, InMemorySearchService, QRisk3Service, QRiskService, Score2Service, SmartRiskService, ValueSetService}
import io.onfhir.cds.service.{CdsServiceContext, ICdsService, ICdsServiceFactory}

object CdsServiceFactory extends ICdsServiceFactory {

  /**
   * Service IDs implemented in the project
   */
  val servicesSupported: Set[String] = Set("qrisk", "definition", "score2", "smart", "qrisk3", "acc_aha", "advance",
    "valueset", "in-memory-search")

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
      case other => throw new Exception(s"Service $other not supported!")
    }
  }
}
