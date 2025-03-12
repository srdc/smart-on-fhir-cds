package srdc.smartcds.cds.service

import io.onfhir.api.Resource
import io.onfhir.api.parsers.FHIRSearchParameterValueParser
import io.onfhir.api.util.ResourceChecker
import io.onfhir.cds.api.model.CdsResponse
import io.onfhir.cds.service.{BaseCdsService, CdsServiceContext, CdsServiceRequest}
import org.json4s.JValue
import srdc.smartcds.config.SmartCdsConfig
import srdc.smartcds.util.Json4sFormatter.formats
import srdc.smartcds.util.{CdsPrefetchUtil, SHCParser}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

abstract class SHCBaseService(cdsServiceContext: CdsServiceContext) extends BaseCdsService(cdsServiceContext) {

  /**
   * Id of the service to be overridden in the extending class
   * Used to determine required prefetches
   */
  def serviceId: String;

  /**
   * Adds the resources from Smart Health Cards into the cdsServiceRequest before continuing the execution with executeCdsWithParsedSHCRequest
   * @param cdsServiceRequest
   * @param ex
   * @return
   */
  override final def executeCds(cdsServiceRequest: CdsServiceRequest)(implicit ex: ExecutionContext): Future[CdsResponse] = {
    val _cdsServiceRequest = if (cdsServiceRequest.contextParams.contains("shc")) {
      val resources = cdsServiceRequest.getArrayContext("shc").extract[Array[String]].flatMap(SHCParser.getBundle)
        .flatMap(bundle => Try((bundle \ "entry").extract[Array[JValue]]).toOption).flatten
        .flatMap(entry => Try((entry \ "resource").extract[JValue]).toOption)
      val prefetchDefinition = SmartCdsConfig.cdsServiceDefinitions(serviceId)
      val newPrefetch = prefetchDefinition.prefetch.map {
        case (key, query) =>
          val (resourceType, id, queryParams) = CdsPrefetchUtil.parseUrlSegment(query)
          if (resourceType == null || cdsServiceRequest.prefetches(key).exists(_ != null) || resources.isEmpty) {
            (key -> cdsServiceRequest.prefetches(key))
          } else {
            val matched = if (id.nonEmpty) {
              resources.filter(resource => Try((resource \ "resourceType").extract[String]).toOption.contains(resourceType)
                && (Try((resource \ "id").extract[String]).toOption.getOrElse(id.get) == id.get)).toSeq
            } else if (queryParams.nonEmpty) {
              val params = queryParams.get.filter(param => param._1 != "_count" && param._1 != "_sort")
                .map(CdsPrefetchUtil.replaceContextParams(_, cdsServiceRequest.contextParams)).toMap
              val parsedSearchParams = FHIRSearchParameterValueParser.parseSearchParameters(resourceType, params).filter(_.paramType != "reference")
              println(key, parsedSearchParams)
              resources.filter(resource =>
                Try((resource \ "resourceType").extract[String]).toOption.contains(resourceType)
              ).filter(resource => {
                ResourceChecker.checkIfResourceSatisfies(resourceType, parsedSearchParams, resource.extract[Resource])
              }).toSeq
            } else {
              resources.toSeq
            }
            println(key, matched.length)
            (key -> matched.map(_.extract[Resource]))
          }
      }
      new CdsServiceRequest(cdsServiceRequest.contextParams, newPrefetch, cdsServiceRequest.preferredLang)
    } else {
      cdsServiceRequest
    }
    executeCdsWithParsedSHCRequest(_cdsServiceRequest)
  }

  /**
   * Execute the request with the included SHC data (to be overridden in the extending classes)
   * @param request
   * @param ex
   * @return
   */
  def executeCdsWithParsedSHCRequest(request: CdsServiceRequest)(implicit ex: ExecutionContext): Future[CdsResponse]
}