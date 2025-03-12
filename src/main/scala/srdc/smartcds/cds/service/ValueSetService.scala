package srdc.smartcds.cds.service

import io.onfhir.cds.api.model.{Card, CdsResponse}
import io.onfhir.cds.service.{BaseCdsService, CdsServiceContext, CdsServiceRequest}
import srdc.smartcds.util.Json4sFormatter._
import org.json4s.DefaultFormats
import org.json4s.JsonAST.JObject
import srdc.smartcds.config.SmartCdsConfig
import srdc.smartcds.model.fhir.{Coding, Compose, Concept, ValueSet}
import srdc.smartcds.util.{DateTimeUtil, DefinitionUtil}

import scala.collection.JavaConverters.asScalaSetConverter
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}

class ValueSetService(cdsServiceContext: CdsServiceContext) extends BaseCdsService(cdsServiceContext) {
  implicit val formats: DefaultFormats.type = DefaultFormats

  /**
   * @param cdsServiceRequest Request should include the ID of the requested service
   * @param ex
   * @return CDS Response with the required value sets for the SHC creation request
   */
  override def executeCds(cdsServiceRequest: CdsServiceRequest)(implicit ex: ExecutionContext): Future[CdsResponse] = {

    val responseBuilder = createResponse(cdsServiceRequest)

    val serviceId = cdsServiceRequest.contextParams("serviceId").extract[String]
    val serviceConfig = SmartCdsConfig.config.getConfig("onfhir.cds.services").getConfig(serviceId)
    val concepts = serviceConfig.entrySet().asScala.map(_.getKey).toList
    val map = mutable.Map[String, List[String]]()
    concepts.foreach(key => {
      serviceConfig.getStringList(key).forEach(coding => {
        val c = coding.split("\\|")
        if (c.length > 1) {
          map.update(c.head, map.getOrElse(c.head, List()) :+ c.last)
        } else {
          map.update("unknown", map.getOrElse("unknown", List()) :+ c.last)
        }
      })
    })

    Future {
      try {
        val cdsResponse = responseBuilder.withCard(_.loadCardWithPostTranslation("card-info",
          "serviceId" -> serviceId,
          "valueSet" -> "tmp"
        )).cdsResponse

        cdsResponse.copy(cards = cdsResponse.cards.map(card =>
          card.copy(suggestions = card.suggestions.map(suggestion =>
            suggestion.copy(actions = suggestion.actions.map(action =>
              action.copy(resource = Some(
                Left(
                  ValueSet(
                    id = Some(serviceId),
                    url = Some("http://kroniq.srdc.com.tr/cds/ValueSet/" + serviceId),
                    status = Some("draft"),
                    date = Some(DateTimeUtil.zonedNow()),
                    compose = Compose(Some(map.keySet.map(key => { Concept(Some(key), map.get(key).map(codes => codes.map(Coding(_, None, None)))) }).toSeq))
                  ).toJson.parseJson.asInstanceOf[JObject]
              )))
            ))
          ))
        ))

      } catch {
        case err: Throwable =>
          err.printStackTrace()
          throw err
      }
    }

  }
}
