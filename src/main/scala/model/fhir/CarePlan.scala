package model.fhir

import util.CarePlanIntent.CarePlanIntent
import util.FHIRStatus.FHIRStatus
import util.JsonClass

final case class CarePlan(resourceType: String,
                          meta: Meta,
                          id: String,
                          status: FHIRStatus,
                          intent: CarePlanIntent,
                          category: Option[Seq[CodeableConcept]],
                          title: Option[String],
                          subject: Reference,
                          period: Option[Period],
                          author: Option[Reference],
                          careTeam: Option[Seq[Reference]],
                          var activity: Option[Seq[CarePlanActivity]],
                          goal: Option[Seq[Reference]],
                          note: Option[Seq[Annotation]]
                         ) extends JsonClass

final case class CarePlanActivity(reference: Option[Reference]) extends JsonClass
