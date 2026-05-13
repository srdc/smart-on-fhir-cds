package srdc.smartcds.cds.service

import io.onfhir.Onfhir.actorSystem
import io.onfhir.cds.api.model.CdsResponse
import io.onfhir.cds.service.{BaseCdsService, CdsServiceContext, CdsServiceRequest}
import io.onfhir.util.JsonFormatter._
import io.onfhir.util.JsonFormatter.formats
import org.json4s.JsonAST.{JArray, JDouble, JObject, JString}
import srdc.smartcds.cds.client.LLMClient
import srdc.smartcds.model.fhir.Observation

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import org.json4s.JsonDSL._
import org.json4s._
import org.json4s.jackson.Serialization
import srdc.smartcds.model.llm.Explanation

class ShapExplainService(cdsServiceContext: CdsServiceContext) extends BaseCdsService(cdsServiceContext) {

  override def executeCds(cdsServiceRequest: CdsServiceRequest)(implicit ex: ExecutionContext): Future[CdsResponse] = {
    val riskPredictions = cdsServiceRequest.getObjectContext("riskPredictions")
//    val chartType = Try(cdsServiceRequest.getStringContext("chartType")).toOption
    val sessionId = Try(cdsServiceRequest.getStringContext("sessionId")).toOption
    val responseBuilder = createResponse(cdsServiceRequest)

//    val shap = if (riskPredictions.isEmpty) None else Some(JArray(riskPredictions.map(obs => {
//      val disease = obs.code.text.get
//      val diseaseRisk = obs.valueQuantity.get.value.get
//      val topIncreasingRiskFactors = obs.component.get.filter(_.valueQuantity.exists(_.value.get > 0)).map(f => (f.code.text.get, f.valueQuantity.get.value.get)).sortBy(_._2).reverse
//      val topDecreasingRiskFactors = obs.component.get.filter(_.valueQuantity.exists(_.value.get < 0)).map(f => (f.code.text.get, f.valueQuantity.get.value.get)).sortBy(_._2)
//      JObject(
//        "condition" -> JString(disease),
//        "risk_probability" -> JDouble(diseaseRisk),
//        "top_increasing_factors" -> JArray(topIncreasingRiskFactors.map(f => JObject(
//          "feature" -> JString(f._1),
//          "contribution" -> JDouble(f._2)
//        )).toList),
//        "top_decreasing_factors" -> JArray(topDecreasingRiskFactors.map(f => JObject(
//          "feature" -> JString(f._1),
//          "contribution" -> JDouble(f._2)
//        )).toList)
//      )
//    }).toList).toJson)

    LLMClient.shapExplain(sessionId, Some(riskPredictions)) map { response =>
      val withCard = responseBuilder.withCard(_.loadCardWithPostTranslation("card-llm-response",
        "text" -> response.text.replaceAll("\n", "\\\\n"),
        "sessionId" -> response.session_id,
        "suggestedQuestions" -> toCdsHooksSuggestions(response.suggested_questions, response.explanation)
      ))
      withCard.cdsResponse
    }

  }

  private def toCdsHooksSuggestions(suggestions: List[String], explanations: Option[Explanation]) = {
    JArray(
      if (suggestions.isEmpty) List.empty
      else suggestions.zipWithIndex.map{ case (suggestion, i) =>
        ("label" -> suggestion) ~ ("uuid" -> s"suggested-question-$i")
      } ++ explanations.map(explanation => {
        List(
          explanation.plot_overview.map(value => ("label" -> value) ~ ("uuid" -> "overview")),
          explanation.one_sentence_summary.map(value => ("label" -> value) ~ ("uuid" -> "summary")),
          explanation.what_increases_prediction.map(value => ("label" -> Serialization.write(value)) ~ ("uuid" -> "increasing-factors")),
          explanation.what_decreases_prediction.map(value => ("label" -> Serialization.write(value)) ~ ("uuid" -> "decreasing-factors")),
          explanation.confidence_and_caveats.map(value => ("label" -> Serialization.write(value)) ~ ("uuid" -> "confidence")),
          explanation.what_this_does_not_mean.map(value => ("label" -> value) ~ ("uuid" -> "what-does-not-mean")),
        ).filter(_.nonEmpty).map(_.get)
      }).getOrElse(List.empty)
    ).toJson
  }

//  private def toCdsHooksSuggestions(suggestions: List[String]) = {
//    if (suggestions.isEmpty) List.empty
//    else suggestions.zipWithIndex.map{ case (suggestion, i) => Map(
//      "value" -> suggestion,
//      "id" -> s"suggested-question-$i"
//    ) ++ (if ((i + 1) == suggestions.size) Map("last" -> true) else Map.empty) }
//  }
}
