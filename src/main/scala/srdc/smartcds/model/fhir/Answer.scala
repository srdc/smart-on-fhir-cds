package srdc.smartcds.model.fhir

case class Answer(valueCoding: Option[Coding] = None,
                  valueInteger: Option[Int] = None,
                  valueDecimal: Option[Double] = None,
                  // Needed to read onfhir-feast's rendered QuestionnaireResponse (stage_wp6_model_features):
                  // boolean features (e.g. Fractured bones) come back as valueBoolean, and any coded feature
                  // that isn't bound to a declared valueSet on the source FeatureGroup falls back to
                  // valueString rather than valueCoding (see QuestionnaireResponseFormatter.getItemAnswer).
                  valueBoolean: Option[Boolean] = None,
                  valueString: Option[String] = None)
