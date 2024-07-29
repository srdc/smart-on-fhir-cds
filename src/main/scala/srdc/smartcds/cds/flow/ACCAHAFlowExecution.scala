package srdc.smartcds.cds.flow

import io.onfhir.cds.model.CdsResponseBuilder
import srdc.smartcds.model.fhir.{Condition, MedicationStatement, Observation, Patient}
import srdc.smartcds.util.{DateTimeUtil, FhirParseHelper}

import scala.math.{exp, log, pow}
import scala.util.Try

/*

This implementation of the algorithm is based on the guidelines provided in the following publication:

Goff DC Jr, Lloyd-Jones DM, Bennett G, Coady S, D’Agostino RB Sr, Gibbons R, Greenland P, Lackland DT, Levy D, O’Donnell CJ,
Robinson JG, Schwartz JS, Shero ST, Smith SC Jr, Sorlie P, Stone NJ, Wilson PWF.
2013 ACC/AHA guideline on the assessment of cardiovascular risk: a report of the American College of Cardiology/American Heart Association Task Force on
Practice Guidelines. Circulation. 2014;129(suppl 2):S49-S73.

© 2013 The Expert Work Group Members. This work is licensed under the Creative Commons Attribution Non-Commercial-NoDerivs License.
You may not use this work for commercial purposes, and you may not modify this work.
For any use, distribution, and reproduction in any medium, you must properly cite the original work.

For more information, visit: https://creativecommons.org/licenses/by-nc-nd/3.0/

*/

//noinspection DuplicatedCode
object ACCAHAFlowExecution {

  /**
   * Executes ACC/AHA calculation service
   *
   * @param patient               Patient resource
   * @param TotalCholesterol      Total Cholesterol Observation
   * @param HDLCholesterol        HDL Observation
   * @param SystolicBP            Blood Pressure Observation
   * @param SmokingStatus         Smoking Status Observation
   * @param Type1Diabetes         Type 1 Diabetes Condition
   * @param Type2Diabetes         Type 2 Diabetes Condition
   * @param HypertensiveTreatment Hypertensive Treatment Medication Statement
   * @param Ethnicity             Ethnicity Observation for Patient
   * @param responseBuilder       Response Builder
   * @return if applicable, returns the related ACC/AHA Score Card as a pair, for patient and
   *         for healthy person of same gender, race and sex; else 'no-value'
   */
  def executeFlow(patient: Patient, TotalCholesterol: Seq[Observation], HDLCholesterol: Seq[Observation],
                  SystolicBP: Seq[Observation], SmokingStatus: Seq[Observation], Type1Diabetes: Seq[Condition], Type2Diabetes: Seq[Condition],
                  HypertensiveTreatment: Seq[MedicationStatement], Ethnicity: Seq[Observation],
                  responseBuilder: CdsResponseBuilder): CdsResponseBuilder = {

    var output = responseBuilder
    val riskScores = calculateACCRisk(patient, TotalCholesterol, HDLCholesterol, SystolicBP, SmokingStatus,
      Type1Diabetes, Type2Diabetes, HypertensiveTreatment, Ethnicity)

    riskScores match {
      case Some((patientScore, healthyScore)) =>
        output = output.withCard(_.loadCardWithPostTranslation("card-score",
          "effectiveDate" -> DateTimeUtil.zonedNow(),
          "patientScoreValue" -> patientScore,
          "healthyScoreValue" -> healthyScore
        ))
        output = recommendStopSmokingIfApplicable(SmokingStatus, output)
        output = recommendReduceBPIfApplicable(SystolicBP, output)
    }

    output
  }

  /**
   * Recommends blood pressure reduction in case of need
   *
   * @param BP_SBP Systolic Blood Pressure of the patient
   * @param output CdsResponseBuilder object
   * @return whether or not SBP is too high and should be reduced
   */
  private def recommendReduceBPIfApplicable(BP_SBP: Seq[Observation], output: CdsResponseBuilder) = {
    val sbp = FhirParseHelper.getSystolicBP(BP_SBP).get
    if (sbp > 140) {
      output.withCard(_.loadCardWithPostTranslation("card-reduce-bp",
        "effectiveDate" -> DateTimeUtil.zonedNow()
      ))
    } else {
      output
    }
  }

  /**
   * Recommends whether patient should stop smoking or not
   *
   * @param SmokingStatus patient's current smoking status
   * @param output        CdsResponseBuilder object
   * @return whether or not patient should stop smoking
   */
  private def recommendStopSmokingIfApplicable(SmokingStatus: Seq[Observation], output: CdsResponseBuilder): CdsResponseBuilder = {
    /*
    I modified the condition because my algorithm only cares whether you
    smoke or not, and my determineSmokingStatus function returns 1 if patient
    is considered a smoker and 0 otherwise.
    */
    if (determineSmokingStatus(SmokingStatus.headOption) > 0) {
      output.withCard(_.loadCardWithPostTranslation("card-stop-smoking",
        "effectiveDate" -> DateTimeUtil.zonedNow()
      ))
    } else {
      output
    }
  }

  /**
   * Checks whether the needed resource is present or not
   *
   * @param resources Resource to check the state of sequences
   * @return 1 if nonempty 0 otherwise
   */
  def checkExists(resources: Seq[Any]): Int = if (resources.nonEmpty) 1 else 0

  /**
   * Determines the race of the patient based on Ethnicity observation
   *
   * @param ethnicity sequence of Ethnicity observations for patient
   *                  Can be further optimized since ethnicity doesn't change
   * @return race of the patient, only two are considered valid
   */
  private def determineRace(ethnicity: Seq[Observation]): String = {
    val blackEthnicityCodes = Seq("LA6162-7")
    /* Black or African-American LOINC code */
    val ethnicityCodes = ethnicity.flatMap(_.valueCodeableConcept.toSeq).flatMap(_.coding.map(_.code))

    /* Paper only cares whether the patient is black or not, so did I */
    if (blackEthnicityCodes.intersect(ethnicityCodes).nonEmpty) "africanamerican" else "white"
  }

  /**
   * Determines the smoking status of the patient
   *
   * @param smokingObs patient's smoking observation
   * @return patient's current smoking status, 1 if smokes and 0 otherwise
   */
  //noinspection DuplicatedCode
  private def determineSmokingStatus(smokingObs: Option[Observation]): Int = {
    val smoking = if (smokingObs.isDefined && smokingObs.get.valueCodeableConcept.isDefined) {
      smokingObs.get.valueCodeableConcept.get.coding.map(_.code).toSeq
    } else {
      Seq("266919005")
    }

    val smoke_cat = if (Seq("LA18978-9", "LA18980-5", "266919005").intersect(smoking).nonEmpty) 0
    else if (smoking.contains("LA15920-4", "8517006")) 1
    else if (Seq("LA18977-1", "LA18982-1").intersect(smoking).nonEmpty) 2
    else if (Seq("LA18979-7", "LA18976-3", "449868002").intersect(smoking).nonEmpty) 3
    else if (smoking.contains("LA18981-3")) 4
    else 0

    /* Treat "never smoked" and "former smoker" as 0, others as 1 */
    if (smoke_cat == 0 || smoke_cat == 1) 0 else 1
  }

  /**
   * Validates given prefetch and returns the ACC/AHA risk score
   *
   * @param patient               Patient resource
   * @param TotalCholesterol      Total Cholesterol Observation
   * @param HDLCholesterol        HDL Observation
   * @param SystolicBP            Blood Pressure Observation
   * @param SmokingStatus         Smoking Status Observation
   * @param Type1Diabetes         Type 1 Diabetes Condition
   * @param Type2Diabetes         Type 2 Diabetes Condition
   * @param HypertensiveTreatment Hypertensive Treatment Medication Statement
   * @param Ethnicity             Ethnicity Observation for Patient
   * @return A double tuple consisting of patient's risk score and healthy score of a hypothetical
   *         patient with same age, sex, gender, but with optimal health parameters
   */
  private def calculateACCRisk(patient: Patient, TotalCholesterol: Seq[Observation], HDLCholesterol: Seq[Observation],
                               SystolicBP: Seq[Observation], SmokingStatus: Seq[Observation], Type1Diabetes: Seq[Condition], Type2Diabetes: Seq[Condition],
                               HypertensiveTreatment: Seq[MedicationStatement], Ethnicity: Seq[Observation]): Option[(Double, Double)] = {

    /* Get related information about the patient */
    val age = FhirParseHelper.getAge(patient)
    val gender = patient.gender
    val raceOpt = Option(determineRace(Ethnicity))
    val raceObs = Ethnicity.headOption
    val diabetes = checkExists(Type1Diabetes) | checkExists(Type2Diabetes)
    val treatedHypertension = checkExists(HypertensiveTreatment)

    val totalCholesterolOpt = Try(TotalCholesterol.head.valueQuantity.get.value.get).toOption
    val hdlCholesterolOpt = Try(HDLCholesterol.head.valueQuantity.get.value.get).toOption
    val systolicBPOpt = FhirParseHelper.getSystolicBP(SystolicBP)
    val smokingObs = SmokingStatus.headOption

    if (totalCholesterolOpt.isEmpty || hdlCholesterolOpt.isEmpty || systolicBPOpt.isEmpty ||
      !FhirParseHelper.checkObservationValuesExist(List(smokingObs, raceObs))) {
      println("Missing required data for ACC/AHA Risk Score calculation.")
      return None
    }

    val totalCholesterol = totalCholesterolOpt.get
    val hdlCholesterol = hdlCholesterolOpt.get
    val sbp = systolicBPOpt.get
    val race = raceOpt.get

    val smoker = determineSmokingStatus(smokingObs)

    gender match {
      case Some("male") =>
        val patientScore = calculateACCRiskM(age, totalCholesterol, hdlCholesterol, sbp, smoker, diabetes, treatedHypertension, race)
        val healthyScore = calculateACCRiskM(age, 170, 50, 110, 0, 0, 0, race) /* Ideal parameters for male patients */
        Some(patientScore, healthyScore)
      case Some("female") =>
        val patientScore = calculateACCRiskF(age, totalCholesterol, hdlCholesterol, sbp, smoker, diabetes, treatedHypertension, race)
        val healthyScore = calculateACCRiskF(age, 170, 50, 110, 0, 0, 0, race) /* Ideal parameters for female patients */
        Some(patientScore, healthyScore)
      case _ =>
        println("Gender not specified or invalid")
        None
    }
  }

  /**
   * Calculate ACC/AHA risk score for male patients
   *
   * @param age                 age of patient
   * @param totalCholesterol    total cholesterol of the patient
   * @param hdlCholesterol      total HDL (High-Density Lipoprotein) cholesterol of the patient
   * @param sbp                 systolic blood pressure of the patient
   * @param smoker              whether patient is an active smoker or not
   * @param diabetes            whether patient has diabetes or not (does NOT care about type of diabetes)
   * @param treatedHypertension whether patient is under treatment related to Hypertension
   * @param race                race of the patient, either "africanamerican" or "white", as per the source paper
   * @return the CVD risk for patient, in percent
   */
  private def calculateACCRiskM(age: Int, totalCholesterol: Double, hdlCholesterol: Double, sbp: Double, smoker: Double,
                                diabetes: Int, treatedHypertension: Int, race: String): Double = {

    /* Log patient params for easier debugging */
    println(s"Calculating ACC Risk for male with values: age=$age, totalCholesterol=$totalCholesterol," +
      s"hdlCholesterol=$hdlCholesterol, sbp=$sbp, smoker=$smoker, diabetes=$diabetes, treatedHypertension=$treatedHypertension, race=$race")

    val lnAge = log(age)
    val lnTotalCholesterol = log(totalCholesterol)
    val lnHDLCholesterol = log(hdlCholesterol)
    val lnSBP = log(sbp)

    val (coefLnAge, coefLnAgeSquared, coefLnTotalCholesterol, coefLnAgeLnTotalCholesterol, coefLnHDLCholesterol,
    coefLnAgeLnHDLCholesterol, coefLnTreatedSBP, coefLnAgeLnTreatedSBP, coefLnUntreatedSBP, coefLnAgeLnUntreatedSBP,
    smokerCoefficient, coefLnAgeSmoker, diabetesCoefficient, baselineSurvival, mean) = race match {
      case "africanamerican" => (
        2.469, /* lnAge */
        0.0, /* lnAgeSquared */
        0.302, /* lnTotalCholesterol */
        0.0, /* lnAge * lnTotalCholesterol */
        -0.307, /* lnHDLCholesterol */
        0.0, /* lnAge * lnHDLCholesterol */
        1.916, /* lnTreatedSBP */
        0.0, /* lnAge * lnTreatedSBP */
        1.809, /* lnUntreatedSBP */
        0.0, /* lnAge * lnUntreatedSBP */
        if (smoker == 1) 0.549 else 0.0, /* smoker */
        0.0, /* lnAge * smoker */
        if (diabetes == 1) 0.645 else 0.0, // diabetes
        0.8954, /* baselineSurvival */
        19.54 /* mean */
      )
      case _ => (
        12.344, /* lnAge */
        0.0, /* lnAgeSquared */
        11.853, /* lnTotalCholesterol */
        -2.664, /* lnAge * lnTotalCholesterol */
        -7.990, /* lnHDLCholesterol */
        1.769, /* lnAge * lnHDLCholesterol */
        1.797, /* lnTreatedSBP */
        0.0, /* lnAge * lnTreatedSBP */
        1.764, /* lnUntreatedSBP */
        0.0, /* lnAge * lnUntreatedSBP */
        if (smoker == 1) 7.837 else 0.0, // smoker
        -1.795, /* lnAge * smoker */
        if (diabetes == 1) 0.658 else 0.0, // diabetes
        0.9144, /* baselineSurvival */
        61.18 /* mean */
      )
    }

    /* Sum the values with respect to the table in the paper */
    val lnSum = coefLnAge * lnAge +
      coefLnAgeSquared * pow(lnAge, 2) +
      coefLnTotalCholesterol * lnTotalCholesterol +
      coefLnAgeLnTotalCholesterol * lnAge * lnTotalCholesterol +
      coefLnHDLCholesterol * lnHDLCholesterol +
      coefLnAgeLnHDLCholesterol * lnAge * lnHDLCholesterol +
      (if (treatedHypertension == 1) coefLnTreatedSBP else coefLnUntreatedSBP) * lnSBP +
      (if (treatedHypertension == 1) coefLnAgeLnTreatedSBP else coefLnAgeLnUntreatedSBP) * lnAge * lnSBP +
      smokerCoefficient +
      coefLnAgeSmoker * lnAge * smoker +
      diabetesCoefficient

    100 * (1 - pow(baselineSurvival, exp(lnSum - mean)))
  }

  /**
   * Calculate ACC/AHA risk score for female patients
   *
   * @param age                 age of patient
   * @param totalCholesterol    total cholesterol of the patient
   * @param hdlCholesterol      total HDL (High-Density Lipoprotein) cholesterol of the patient
   * @param sbp                 systolic blood pressure of the patient
   * @param smoker              whether patient is an active smoker or not
   * @param diabetes            whether patient has diabetes or not (does NOT care about type of diabetes)
   * @param treatedHypertension whether patient is under treatment related to Hypertension
   * @param race                race of the patient, either "africanamerican" or "white", as per the source paper
   * @return the CVD risk for patient, in percent
   */
  private def calculateACCRiskF(age: Int, totalCholesterol: Double, hdlCholesterol: Double, sbp: Double, smoker: Double,
                                diabetes: Int, treatedHypertension: Int, race: String): Double = {

    /* Log patient params for easier debugging */
    println(s"Calculating ACC Risk for female with values: age=$age, totalCholesterol=$totalCholesterol," +
      s"hdlCholesterol=$hdlCholesterol, sbp=$sbp, smoker=$smoker, diabetes=$diabetes," +
      s"treatedHypertension=$treatedHypertension, race=$race")

    val lnAge = log(age)
    val lnTotalCholesterol = log(totalCholesterol)
    val lnHDLCholesterol = log(hdlCholesterol)
    val lnSBP = log(sbp)

    val (coefLnAge, coefLnAgeSquared, coefLnTotalCholesterol, coefLnAgeLnTotalCholesterol, coefLnHDLCholesterol,
    coefLnAgeLnHDLCholesterol, coefLnTreatedSBP, coefLnAgeLnTreatedSBP, coefLnUntreatedSBP, coefLnAgeLnUntreatedSBP,
    smokerCoefficient, coefLnAgeSmoker, diabetesCoefficient, baselineSurvival, mean) = race match {
      case "africanamerican" => (
        17.114, /* lnAge */
        0.0, /* lnAgeSquared */
        0.940, /* lnTotalCholesterol */
        0.0, /* lnAge * lnTotalCholesterol */
        -18.920, /* lnHDLCholesterol */
        4.475, /* lnAge * lnHDLCholesterol */
        29.291, /* lnTreatedSBP */
        -6.432, /* lnAge * lnTreatedSBP */
        27.820, /* lnUntreatedSBP */
        -6.087, /* lnAge * lnUntreatedSBP */
        if (smoker == 1) 0.691 else 0.0, /* smoker */
        0.0, /* lnAge * smoker */
        if (diabetes == 1) 0.874 else 0.0, /* diabetes */
        0.9533, /* baselineSurvival */
        86.61 /* mean */
      )
      case _ => (
        -29.799, /* lnAge */
        4.884, /* lnAgeSquared */
        13.540, /* lnTotalCholesterol */
        -3.114, /* lnAge * lnTotalCholesterol */
        -13.578, /* lnHDLCholesterol */
        3.149, /* lnAge * lnHDLCholesterol */
        2.019, /* lnTreatedSBP */
        0.0, /* lnAge * lnTreatedSBP */
        1.957, /* lnUntreatedSBP */
        0.0, /* lnAge * lnUntreatedSBP */
        if (smoker == 1) 7.574 else 0.0, /* smoker */
        -1.665, /* lnAge * smoker */
        if (diabetes == 1) 0.661 else 0.0, /* diabetes */
        0.9665, /* baselineSurvival */
        -29.18 /* mean */
      )
    }

    /* Sum the values with respect to the table in the paper */
    val lnSum = coefLnAge * lnAge +
      coefLnAgeSquared * pow(lnAge, 2) +
      coefLnTotalCholesterol * lnTotalCholesterol +
      coefLnAgeLnTotalCholesterol * lnAge * lnTotalCholesterol +
      coefLnHDLCholesterol * lnHDLCholesterol +
      coefLnAgeLnHDLCholesterol * lnAge * lnHDLCholesterol +
      (if (treatedHypertension != 0) coefLnTreatedSBP else coefLnUntreatedSBP) * lnSBP +
      (if (treatedHypertension != 0) coefLnAgeLnTreatedSBP else coefLnAgeLnUntreatedSBP) * lnAge * lnSBP +
      smokerCoefficient +
      coefLnAgeSmoker * lnAge * smoker +
      diabetesCoefficient

    100 * (1 - pow(baselineSurvival, exp(lnSum - mean)))
  }
}