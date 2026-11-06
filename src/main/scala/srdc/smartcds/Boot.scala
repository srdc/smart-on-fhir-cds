package srdc.smartcds
import io.onfhir.cds.OnFhirCds
import io.onfhir.config.FSConfigReader
import io.onfhir.r4.config.FhirR4Configurator
import srdc.smartcds.cds.CdsServiceFactory
import srdc.smartcds.config.{LlmConfig, SmartCdsConfig}

object Boot extends App {
  // Initialize FHIR version for the in-memory search
  val fhirConfigurator = new FhirR4Configurator()

  val fileSystemConfigReader = new FSConfigReader(fhirVersion = "R4")

  val fhirServerConfig = fhirConfigurator.initializeServerPlatform(fileSystemConfigReader, Set(
    "http://hl7.org/fhir/OperationDefinition/Resource-validate",
    "http://hl7.org/fhir/OperationDefinition/Resource-meta-add",
    "http://hl7.org/fhir/OperationDefinition/Resource-meta",
    "http://hl7.org/fhir/OperationDefinition/Composition-document",
    "http://hl7.org/fhir/OperationDefinition/Observation-lastn",
    "http://hl7.org/fhir/OperationDefinition/ValueSet-expand",
    "http://hl7.org/fhir/OperationDefinition/Resource-meta-delete",
    "http://hl7.org/fhir/OperationDefinition/Patient-everything"
  ))
  SmartCdsConfig.fhirServerConfig = Some(fhirServerConfig)

  val onfhirCds = OnFhirCds.asStandaloneServer(CdsServiceFactory)
  // Import service definitions to be used internally
  SmartCdsConfig.cdsServiceDefinitions = onfhirCds.getConfig().cdsServiceDefinitions
  onfhirCds.start()
}