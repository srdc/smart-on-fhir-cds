package srdc.smartcds.model.fhir

case class Answer(valueCoding: Option[Coding] = None, valueInteger: Option[Int] = None, valueDecimal: Option[Double] = None)
