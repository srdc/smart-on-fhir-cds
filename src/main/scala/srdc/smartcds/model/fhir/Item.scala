package srdc.smartcds.model.fhir

case class Item(linkId: String,
                prefix: Option[String] = None,
                text: Option[String] = None,
                `type`: Option[String] = None,
                required: Option[Boolean] = None,
                item: Option[Array[Item]] = None,
                answerOption: Option[Array[AnswerOption]] = None,
                answer: Option[Array[Answer]] = None,
                code: Option[Array[Coding]] = None)
