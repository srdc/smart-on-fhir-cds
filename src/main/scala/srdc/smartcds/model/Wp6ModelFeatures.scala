package srdc.smartcds.model

/**
 * linkIds (= outputName) used by onfhir-feast's `stage_wp6_model_features` FeatureSet, as rendered in the
 * QuestionnaireResponse returned by its `$retrieve-feature-values` operation (format=fhir).
 *
 * Unlike the old, hand-maintained UK Biobank prefetch mapping (see [[UKBiobankFeatures]]), these linkIds are
 * NOT suffixed with "_set" - that suffix is only used by the human-facing `stage_wp6_features` Questionnaire
 * for its raw multi-select answers (dental problems, pain types, father/mother/sibling family history).
 * `stage_wp6_model_features` reads those saved multi-select answers back and exposes them as plain numeric
 * counts (and two extra booleans, see HAS_DENTURES/HAS_PAIN_ALL_OVER_BODY below), so the multi-select-specific
 * linkIds here carry a count/boolean value directly rather than a coded answer array.
 */
object Wp6ModelFeatures {
  final val SEX = "31"
  final val AGE = "21003"
  final val WAIST_CIRCUMFERENCE = "48"
  final val HIP_CIRCUMFERENCE = "49"
  final val STANDING_HEIGHT = "50"
  final val WEIGHT = "21002"
  final val BMI = "21001"
  final val BODY_FAT_PERCENTAGE = "23099"
  // Systolic/diastolic blood pressure keep the automated and manual UK Biobank fields separate (unlike the
  // old questionnaire's single synthetic "sbp"/"dbp" field) - prefer the automated reading, falling back to
  // the manual one, mirroring the existing SystolicBP/DiastolicBP combination already used for
  // risk_prediction_form (see SmartCdsConfig.sbpCodes/dbpCodes).
  final val SYSTOLIC_BP_AUTOMATED = "4080"
  final val SYSTOLIC_BP_MANUAL = "93"
  final val DIASTOLIC_BP_AUTOMATED = "4079"
  final val DIASTOLIC_BP_MANUAL = "94"
  final val PULSE_RATE = "95"
  final val SLEEP = "1160"
  final val CHOLESTEROL = "30690"
  final val HDL_CHOLESTEROL = "30760"
  final val LDL = "30780"
  final val TRIGLYCERIDES = "30870"
  final val APOLIPOPROTEIN_A = "30630"
  final val APOLIPOPROTEIN_B = "30640"
  final val GLUCOSE = "30740"
  final val HBA1C = "30750"
  final val CRP = "30710"
  final val IGF1 = "30770"
  final val TESTOSTERONE = "30850"
  final val MICROALBUMIN_URINE = "30500"
  final val CREATININE_URINE = "30510"
  final val POTASSIUM_URINE = "30520"
  final val SODIUM_URINE = "30530"
  final val ALBUMIN = "30600"
  final val ALANINE_AMINOTRANSFERASE = "30620"
  final val ASPARTATE_AMINOTRANSFERASE = "30650"
  final val DIRECT_BILIRUBIN = "30660"
  final val UREA = "30670"
  final val CALCIUM = "30680"
  final val CREATININE = "30700"
  final val CYSTATIN_C = "30720"
  final val GAMMA_GLUTAMYLTRANSFERASE = "30730"
  final val LIPOPROTEIN_A = "30790"
  final val OESTRADIOL = "30800"
  final val PHOSPHATE = "30810"
  final val SHBG = "30830"
  final val TOTAL_BILIRUBIN = "30840"
  final val FRACTURED_BONES = "2463"
  // Distinct-condition counts (already de-duplicated/aggregated onfhir-feast side) rather than raw
  // multi-select answers.
  final val DENTAL_PROBLEMS_COUNT = "6149"
  final val PAIN_TYPES_COUNT = "6159"
  final val FATHER_ILLNESS_COUNT = "20107"
  final val MOTHER_ILLNESS_COUNT = "20110"
  final val SIBLING_ILLNESS_COUNT = "20111"
  // Added alongside the counts specifically to preserve the two specific-code checks the existing ML model
  // needs (which distinct codes were selected are lost once collapsed to a bare count) - see
  // stage-wp6-model-features.json / build_featureset_b.py.
  final val HAS_DENTURES = "has_dentures"
  final val HAS_PAIN_ALL_OVER_BODY = "has_pain_all_over_body"
  final val MISERABLENESS = "1930"
  final val TENSENESS = "2070"
  final val TIREDNESS = "2080"
  final val GETTING_UP = "1170"
  final val UNENTHUSIASM = "2060"
  final val FED_UP_FEELINGS = "1960"
  final val WORRIER_FEELINGS = "1980"
  final val LONELINESS = "2020"
  final val HEARING_DIFFICULTY = "2247"
}
