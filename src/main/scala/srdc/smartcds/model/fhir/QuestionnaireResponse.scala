package srdc.smartcds.model.fhir

import srdc.smartcds.util.JsonClass

case class QuestionnaireResponse(resourceType: String,
                                 identifier: Option[Identifier],
                                 meta: Option[Meta],
                                 contained: Option[Array[Questionnaire]],
                                 questionnaire: String,
                                 status: String,
                                 item: Option[Array[Item]]) extends JsonClass
