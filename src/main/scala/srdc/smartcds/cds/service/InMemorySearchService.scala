package srdc.smartcds.cds.service

import io.onfhir.api.Resource
import io.onfhir.api.parsers.FHIRSearchParameterValueParser
import io.onfhir.api.util.ResourceChecker
import io.onfhir.cds.api.model.CdsResponse
import io.onfhir.cds.service.{BaseCdsService, CdsServiceContext, CdsServiceRequest}
import io.onfhir.util.JsonFormatter.convertToJson2
import org.json4s.DefaultFormats
import org.json4s.JsonAST.{JArray, JObject}
import srdc.smartcds.config.SmartCdsConfig
import srdc.smartcds.util.CdsPrefetchUtil

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class InMemorySearchService(cdsServiceContext: CdsServiceContext) extends BaseCdsService(cdsServiceContext) {
  implicit val formats: DefaultFormats.type = DefaultFormats

  private val resourceChecker = new ResourceChecker(SmartCdsConfig.fhirServerConfig.get)
  private val searchParameterValueParser = new FHIRSearchParameterValueParser(SmartCdsConfig.fhirServerConfig.get)
  /**
   * @param cdsServiceRequest Request should include the ID of the requested service
   * @param ex
   * @return CDS Response with the resources matched for the given parameters
   */
  override def executeCds(cdsServiceRequest: CdsServiceRequest)(implicit ex: ExecutionContext): Future[CdsResponse] = {

    val responseBuilder = createResponse(cdsServiceRequest)

    val query = cdsServiceRequest.contextParams("query").extract[String]
    val bundle = cdsServiceRequest.getReadPrefetch("bundle")

    Future {
      try {
        val (resourceType, id, queryParams) = CdsPrefetchUtil.parseUrlSegment(query)
        val params = queryParams.get.filter(param => param._1 != "_count" && param._1 != "_sort")
          .map(CdsPrefetchUtil.replaceContextParams(_, cdsServiceRequest.contextParams)).toMap
        val parsedSearchParams = searchParameterValueParser.parseSearchParameters(resourceType, params).filter(_.paramType != "reference")
        val entries = Try((bundle \ "entry").extract[List[JObject]]).toOption.getOrElse(List.empty)
        val resultEntries = entries.filter(entry =>
          Try((entry \ "resource" \ "resourceType").extract[String]).toOption.contains(resourceType)
        ).filter(entry => {
          val resource = Try((entry \ "resource").extract[Resource]).toOption.getOrElse(JObject().asInstanceOf[Resource])
          resourceChecker.checkIfResourceSatisfies(resourceType, parsedSearchParams, resource)
        })
        responseBuilder.withCard(_.loadCardWithPostTranslation("card-info",
          "bundle" -> s"""{ "resourceType": "Bundle", "entry": ${JArray(resultEntries).toJson} }"""
        )).cdsResponse

      } catch {
        case err: Throwable =>
          err.printStackTrace()
          throw err
      }
    }

  }
}
