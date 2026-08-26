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

class AskLlmService(cdsServiceContext: CdsServiceContext) extends BaseCdsService(cdsServiceContext) {

  override def executeCds(cdsServiceRequest: CdsServiceRequest)(implicit ex: ExecutionContext): Future[CdsResponse] = {
    val disease = Try(cdsServiceRequest.getStringContext("disease")).toOption
    val chartType = Try(cdsServiceRequest.getStringContext("chartType")).toOption
    val sessionId = Try(cdsServiceRequest.getStringContext("sessionId")).toOption
    val question = Try(cdsServiceRequest.getStringContext("question")).toOption

    if (question.isEmpty || sessionId.isEmpty) {
      throw new Exception("Question and session ID should be provided!")
    }

    val responseBuilder = createResponse(cdsServiceRequest)

    var structuredQuestion = ""

    if (disease.nonEmpty) {
      structuredQuestion +=
        s"""
          |The user is shown the risk predictions of $disease using the SHAP values provided before.\n
          |""".stripMargin
    }

    if (chartType.nonEmpty) {
      structuredQuestion +=
        s"""
          |The user is shown a graph as following: $chartType\n
          |""".stripMargin
    }

    if (disease.nonEmpty || chartType.nonEmpty) {
      structuredQuestion +=
        s"""
           |Considering the context information above, answer the following question asked by the user:\n
           |""".stripMargin
    }

    structuredQuestion += question

    LLMClient.ask(sessionId.get, question.get) map { response =>
      val withCard = responseBuilder.withCard(
        _.loadCardWithPostTranslation(
          "card-llm-response",
          "text" -> response.text.replaceAll("\n", "\\\\n"),
          "sessionId" -> response.session_id,
          "suggestedQuestions" -> toCdsHooksSuggestions(
            response.suggested_questions
          )
        )
      )
      withCard.cdsResponse
    }

  }

  private def toCdsHooksSuggestions(suggestions: List[String]) = {
    JArray(
      if (suggestions.isEmpty) List.empty
      else suggestions.zipWithIndex.map{ case (suggestion, i) =>
        ("label" -> suggestion) ~ ("uuid" -> s"suggested-question-$i")
      }
    ).toJson
  }
}
