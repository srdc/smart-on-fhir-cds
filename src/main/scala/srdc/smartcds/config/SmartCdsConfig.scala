package srdc.smartcds.config

import com.typesafe.config.{Config, ConfigFactory}
import io.onfhir.cds.api.model.CdsServiceDefinition
import io.onfhir.config.FhirServerConfig

import scala.concurrent.duration.{FiniteDuration, MILLISECONDS}
import scala.util.Try

object SmartCdsConfig {
  /** Application config object. */
  val config = ConfigFactory.load()

  /** Rule Units */
  lazy val ruleUnits: Config = config.getConfig("onfhir.cds.rule-units")

  /** Definitions Path */
  lazy val definitionsPath: Option[String] = Try(config.getString("onfhir.cds.definitions-path")).toOption
  lazy val conceptDefinitionsPath: Option[String] = Try(config.getString("app.concept-definitions-path")).toOption

  /** Bundles Path */
  lazy val bundlesPath: Option[String] = Try(config.getString("app.kroniq-bundles-path")).toOption
  lazy val valueSetsPath: Option[String] = Try(config.getString("app.kroniq-valuesets-path")).toOption

  var cdsServiceDefinitions: Map[String, CdsServiceDefinition] = Map.empty
  var fhirServerConfig: Option[FhirServerConfig] = None

  // EpisodeOfCare.extension urls
  final val STATUS_TO_BE_SET: String = "http://kroniq.srdc.com.tr/fhir/StructureDefinition/status-to-be-set"
  final val EPISODE_TYPE_TO_CONTINUE: String = "http://kroniq.srdc.com.tr/fhir/StructureDefinition/episode-type-to-continue"

  final val shcEnabled: Boolean = Try(config.getBoolean("app.smart-health-cards.enabled")).getOrElse(false)
  final val shcStrictSignatureVerification: Boolean = Try(config.getBoolean("app.smart-health-cards.signature-verification")).getOrElse(false)

  lazy val sbpCodes = Try(SmartCdsConfig.config.getStringList("onfhir.cds.services.risk_prediction_form.SystolicBP")).toOption
  lazy val dbpCodes = Try(SmartCdsConfig.config.getStringList("onfhir.cds.services.risk_prediction_form.DiastolicBP")).toOption

  /** Base URL of the onfhir-feast REST API used to retrieve pre-computed feature values (e.g. for risk_prediction_form) */
  lazy val onfhirFeastUrl: String =
    Try(config.getString("onfhir-feast-service.url")).getOrElse("http://localhost:8085/onfhir-feast/api")
  /** Id of the onfhir-feast DataSource to query */
  lazy val onfhirFeastDataSourceId: String =
    Try(config.getString("onfhir-feast-service.dataSourceId")).getOrElse("myFhirServer")
  /** Id of the onfhir-feast FeatureSet definition to compute (human-facing questionnaire prefill) */
  lazy val onfhirFeastFeatureSetId: String =
    Try(config.getString("onfhir-feast-service.featureSetId")).getOrElse("stage_wp6_features")
  /** Id of the onfhir-feast FeatureSet definition for model-ready features, read back from the saved
   * QuestionnaireResponse (used by risk_prediction) */
  lazy val onfhirFeastModelFeatureSetId: String =
    Try(config.getString("onfhir-feast-service.modelFeatureSetId")).getOrElse("stage_wp6_model_features")
  /** Base URL of the ML (shap-client) service; ml-service.url, overridable with ML_SERVICE_URL */
  lazy val mlServiceUrl: String =
    Try(config.getString("ml-service.url")).getOrElse("http://localhost:8001")
  /** Max time to wait for the ML service to respond */
  lazy val mlServiceTimeout: FiniteDuration =
    Try(FiniteDuration(config.getDuration("ml-service.timeout").toMillis, MILLISECONDS))
      .getOrElse(FiniteDuration(30000, MILLISECONDS))

  /** Max time to wait for onfhir-feast's $retrieve-feature-values operation to respond */
  lazy val onfhirFeastTimeout: FiniteDuration =
    Try(FiniteDuration(config.getDuration("onfhir-feast-service.timeout").toMillis, MILLISECONDS))
      .getOrElse(FiniteDuration(120000, MILLISECONDS))

}
