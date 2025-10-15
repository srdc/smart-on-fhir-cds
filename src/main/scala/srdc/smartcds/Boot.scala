package srdc.smartcds
import io.onfhir.cds.OnFhirCds
import io.onfhir.config.FSConfigReader
import io.onfhir.r4.config.FhirR4Configurator
import srdc.smartcds.cds.CdsServiceFactory
import srdc.smartcds.config.SmartCdsConfig

object Boot extends App {
  // Initialize FHIR version for the in-memory search
  val fhirConfigurator = new FhirR4Configurator()

  val fileSystemConfigReader = new FSConfigReader(fhirVersion = "R4")

  val fhirServerConfig = fhirConfigurator.initializeServerPlatform(fileSystemConfigReader, Map(
    "http://hl7.org/fhir/OperationDefinition/Resource-validate" -> "io.onfhir.operation.ValidationOperationHandler",
    "http://hl7.org/fhir/OperationDefinition/Resource-meta-add" -> "io.onfhir.operation.MetaOperationHandler",
    "http://hl7.org/fhir/OperationDefinition/Resource-meta" -> "io.onfhir.operation.MetaOperationHandler",
    "http://hl7.org/fhir/OperationDefinition/Composition-document" -> "io.onfhir.operation.DocumentOperationHandler",
    "http://hl7.org/fhir/OperationDefinition/Observation-lastn" -> "io.onfhir.operation.LastNObservationOperationHandler",
    "http://hl7.org/fhir/OperationDefinition/ValueSet-expand" -> "io.onfhir.operation.ExpandOperationHandler",
    "http://hl7.org/fhir/OperationDefinition/Resource-meta-delete" -> "io.onfhir.operation.MetaOperationHandler",
    "http://hl7.org/fhir/OperationDefinition/Patient-everything" -> "io.onfhir.operation.ExpandOperationHandler"
  ))
  SmartCdsConfig.fhirServerConfig = Some(fhirServerConfig)

  val onfhirCds = OnFhirCds.asStandaloneServer(CdsServiceFactory)
  // Import service definitions to be used internally
  SmartCdsConfig.cdsServiceDefinitions = onfhirCds.getConfig().cdsServiceDefinitions
  onfhirCds.start()
}