package srdc.smartcds
import io.onfhir.cds.OnFhirCds
import io.onfhir.config.FhirConfigurationManager
import io.onfhir.r4.config.FhirR4Configurator
import srdc.smartcds.cds.CdsServiceFactory
import srdc.smartcds.config.SmartCdsConfig

object Boot extends App {
  // Initialize FHIR version for the in-memory search
  FhirConfigurationManager.initialize(new FhirR4Configurator(), Map.empty)
  val onfhirCds = OnFhirCds.asStandaloneServer(CdsServiceFactory)
  // Import service definitions to be used internally
  SmartCdsConfig.cdsServiceDefinitions = onfhirCds.getConfig().cdsServiceDefinitions
  onfhirCds.start()
}