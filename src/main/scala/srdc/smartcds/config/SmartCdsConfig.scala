package srdc.smartcds.config

import com.typesafe.config.{Config, ConfigFactory}
import io.onfhir.cds.api.model.CdsServiceDefinition

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

  // EpisodeOfCare.extension urls
  final val STATUS_TO_BE_SET: String = "http://kroniq.srdc.com.tr/fhir/StructureDefinition/status-to-be-set"
  final val EPISODE_TYPE_TO_CONTINUE: String = "http://kroniq.srdc.com.tr/fhir/StructureDefinition/episode-type-to-continue"

  final val shcEnabled: Boolean = Try(config.getBoolean("app.smart-health-cards.enabled")).getOrElse(false)
  final val shcStrictSignatureVerification: Boolean = Try(config.getBoolean("app.smart-health-cards.signature-verification")).getOrElse(false)

}
