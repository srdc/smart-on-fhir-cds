package srdc.smartcds.config

import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._
import java.util.concurrent.TimeUnit

object LlmConfig {
  private val c = ConfigFactory.load()

  lazy val enabled: Boolean = c.hasPath("llm-service.enabled") && c.getBoolean("llm-service.enabled")

  // Required
  lazy val baseUrl: String = c.getString("llm-service.baseUrl")
  lazy val clientId: String = c.getString("llm-service.clientId")
  lazy val clientSecret: String = c.getString("llm-service.clientSecret")

  lazy val riskPredictionCoding: String = c.getStringList("onfhir.cds.services.risk_prediction.RISK_PREDICTION_CODE").get(0)

  // Optional with default
  val timeout: FiniteDuration =
    if (c.hasPath("llm-service.timeout"))
      c.getDuration("llm-service.timeout", TimeUnit.MILLISECONDS).millis
    else
      20.seconds
}
