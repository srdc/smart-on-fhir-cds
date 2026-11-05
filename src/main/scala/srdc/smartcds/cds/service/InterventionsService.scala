package srdc.smartcds.cds.service

import akka.http.scaladsl.model.StatusCodes
import io.onfhir.cds.api.model.CdsResponse
import io.onfhir.cds.model.CdsException
import io.onfhir.cds.service.{BaseCdsService, CdsServiceContext, CdsServiceRequest}
import org.json4s.DefaultFormats

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class InterventionsService(cdsServiceContext: CdsServiceContext) extends BaseCdsService(cdsServiceContext) {
  implicit val formats: DefaultFormats.type = DefaultFormats

  private final val riskFactors = List(
    Map(
      "disease" -> "Type 2 Diabetes",
      "riskFactors" -> List(
        Map(
          "_first" -> true,
          "id" -> "reduce-carbs",
          "label" -> "Replace white bread, sugary cereals, and white rice with whole-grain options like oats, quinoa, brown rice, and whole-wheat bread, aiming for at least 3 servings of whole grains per day to help regulate blood sugar and support heart health."
        ),
        Map(
          "id" -> "sleep",
          "label" -> "To improve sleep quality, establish a consistent bedtime routine, avoid screens at least 1 hour before bed, and aim for 7–9 hours of sleep per night—if you struggle with sleep, try magnesium-rich foods (nuts, spinach) or herbal teas like chamomile."
        )
      )
    ),
    Map(
      "disease" -> "Cardiovascular Disease",
      "riskFactors" -> List(
        Map(
          "_first" -> true,
          "id" -> "smoking",
          "label" -> "To reduce your risk of cardiovascular disease, consider using nicotine replacement therapy (patches, gum, or lozenges) or joining a smoking cessation program, as quitting smoking can halve your risk of heart disease within a year."
        ),
        Map(
          "id" -> "sleep",
          "label" -> "To improve sleep quality, establish a consistent bedtime routine, avoid screens at least 1 hour before bed, and aim for 7–9 hours of sleep per night—if you struggle with sleep, try magnesium-rich foods (nuts, spinach) or herbal teas like chamomile."
        )
      )
    )
  )

//  private final val interventions = List(
//    Map(
//      "_first" -> true,
//      "label" -> "LDL Cholesterol",
//      "id" -> "ldl",
//      "description" -> "Reduce LDL Cholesterol",
//      "target" -> Map(
//        "system" -> "http://loinc.org",
//        "code" -> "2089-1",
//        "display" -> "Cholesterol in LDL [Moles/volume] in Serum or Plasma"
//      )
//    ),
//    Map(
//      "label" -> "Diet",
//      "id" -> "diet",
//      "description" -> "Assign a Diet Program",
//      "target" -> Map(
//        "system" -> "http://loinc.org",
//        "code" -> "81663-7",
//        "display" -> "Diet"
//      )
//    )
//  )

  private final val interventions = List(
    Map(
      "_first" -> true,
      "label" -> "Daily Steps",
      "id" -> "steps",
      "description" -> "Increase Daily Steps",
      "target" -> Map("system" -> "http://loinc.org", "code" -> "41950-7", "display" -> "Number of steps in 24 hour Measured")
    ),
    Map(
      "label" -> "Nightly Sleep",
      "id" -> "sleep",
      "description" -> "Increase Nightly Sleep Time",
      "target" -> Map("system" -> "http://loinc.org", "code" -> "93832-4", "display" -> "Total sleep duration")
    ),
    Map(
      "label" -> "Weight",
      "id" -> "weight",
      "description" -> "Reduce Weight",
      "target" -> Map("system" -> "http://loinc.org", "code" -> "29463-7", "display" -> "Body weight")
    ),
    Map(
      "label" -> "Blood Pressure",
      "id" -> "blood-pressure",
      "description" -> "Reduce Blood Pressure",
      "target" -> Map("system" -> "http://loinc.org", "code" -> "85354-9", "display" -> "Blood pressure panel")
    ),
    Map(
      "label" -> "Blood Sugar",
      "id" -> "blood-glucose",
      "description" -> "Reduce Blood Sugar",
      "target" -> Map("system" -> "http://loinc.org", "code" -> "2339-0", "display" -> "Glucose [Mass/volume] in Blood")
    ),
    Map(
      "label" -> "HbA1c",
      "id" -> "hba1c",
      "description" -> "Reduce HbA1c",
      "target" -> Map("system" -> "http://loinc.org", "code" -> "4548-4", "display" -> "Hemoglobin A1c/Hemoglobin.total in Blood")
    ),
    Map(
      "label" -> "Cholesterol",
      "id" -> "cholesterol",
      "description" -> "Reduce Cholesterol",
      "target" -> Map("system" -> "http://loinc.org", "code" -> "2093-3", "display" -> "Cholesterol [Moles/volume] in Serum or Plasma")
    ),
    Map(
      "label" -> "HDL",
      "id" -> "hdl",
      "description" -> "Set HDL Target",
      "target" -> Map("system" -> "http://loinc.org", "code" -> "2085-9", "display" -> "Cholesterol in HDL [Moles/volume] in Serum or Plasma")
    ),
    Map(
      "label" -> "LDL",
      "id" -> "ldl",
      "description" -> "Reduce LDL Cholesterol",
      "target" -> Map("system" -> "http://loinc.org", "code" -> "2089-1", "display" -> "Cholesterol in LDL [Moles/volume] in Serum or Plasma")
    ),
    Map(
      "label" -> "Triglycerides",
      "id" -> "triglycerides",
      "description" -> "Reduce Triglycerides",
      "target" -> Map("system" -> "http://loinc.org", "code" -> "2571-8", "display" -> "Triglyceride [Moles/volume] in Serum or Plasma")
    ),
    Map(
      "label" -> "Cigarettes",
      "id" -> "smoking",
      "description" -> "Stop Smoking",
      "target" -> Map("system" -> "http://snomed.info/sct", "code" -> "225323000", "display" -> "Smoking cessation advice")
    ),
    Map(
      "label" -> "Alcohol",
      "id" -> "alcohol",
      "description" -> "Reduce Alcohol Intake",
      "target" -> Map("system" -> "http://loinc.org", "code" -> "105992-2", "display" -> "Alcoholic drinks per week")
    ),
    Map(
      "label" -> "Waist/Hip Circumference",
      "id" -> "waist-circumference",
      "description" -> "Reduce Waist Circumference",
      "target" -> Map("system" -> "http://loinc.org", "code" -> "8280-0", "display" -> "Waist circumference")
    ),
    Map(
      "label" -> "Diet",
      "id" -> "diet",
      "description" -> "Assign a Diet Program",
      "target" -> Map("system" -> "http://loinc.org", "code" -> "81663-7", "display" -> "Diet")
    ),
    Map(
      "label" -> "Record Pain",
      "id" -> "pain",
      "description" -> "Ask Patient to Record Pain",
      "target" -> Map("system" -> "http://loinc.org", "code" -> "106724-8", "display" -> "Pain level")
    ),
    Map(
      "label" -> "Exercise",
      "id" -> "exercise",
      "description" -> "Assign an Exercise Program",
      "target" -> Map("system" -> "http://loinc.org", "code" -> "73985-4", "display" -> "Exercise activity")
    ),
    Map(
      "label" -> "Medicine Tracking",
      "id" -> "medicine",
      "description" -> "Track Medicine Usage",
      "target" -> Map("system" -> "http://loinc.org", "code" -> "71799-1", "display" -> "Medication adherence")
    )
  )

  override def executeCds(cdsServiceRequest: CdsServiceRequest)(implicit ex: ExecutionContext): Future[CdsResponse] = {
    val responseBuilder = createResponse(cdsServiceRequest)
    Future {
      try {
        var resp = responseBuilder.withCard(_.loadCardWithPostTranslation("card-interventions",
          "interventions" -> interventions
        ))
        for (risk <- riskFactors) {
          resp = resp.withCard(_.loadCardWithPostTranslation("card-risk-factors",
            "disease" -> risk("disease"),
            "riskFactors" -> risk("riskFactors")
          ))
        }
        resp.cdsResponse
      } catch {
        case (ex: Exception) =>
          ex.printStackTrace()
          throw ex
      }
    }
  }
}
