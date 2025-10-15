package srdc.smartcds.util

import srdc.smartcds.config.SmartCdsConfig
import srdc.smartcds.model.fhir.Quantity
import srdc.smartcds.util.UnitConceptEnum.UnitConceptEnum

import java.util.Locale
import scala.util.Try

/**
 * Util containing unit conversion methods for observations used in CDS Services
 */
object ObservationUnitUtil {

  /**
   * Get unit used in the CDS rules for the given concept
   *
   * @param concept Unit concept of the observation
   * @return
   */
  def getDefaultUnit(concept: UnitConceptEnum): String = {
    Try(SmartCdsConfig.ruleUnits.getString(concept)).getOrElse("")
  }

  /**
   * Try matching given unit concept with a conversion method and
   * convert the quantity value to the target unit if possible
   *
   * @param concept Unit concept of the observation
   * @param quantity Quantity to be converted
   * @param targetUnit Targeted unit
   * @return Converted quantity
   */
  def handleUnit(concept: UnitConceptEnum, quantity: Quantity, targetUnit: Option[String]): Quantity = {
    val _targetUnit = targetUnit.getOrElse(getDefaultUnit(concept))toLowerCase(Locale.ENGLISH)
    if (_targetUnit.isEmpty) {
      return quantity
    }
    concept match {
      case UnitConceptEnum.ACR => handleACRUnit(quantity, _targetUnit)
      case UnitConceptEnum.CHOLESTEROL => handleCholesterolUnit(quantity, _targetUnit)
      case UnitConceptEnum.HBA1C => handleHbA1CUnit(quantity, _targetUnit)
      case UnitConceptEnum.FASTING_GLUCOSE => handleFastingGlucoseUnit(quantity, _targetUnit)
      case UnitConceptEnum.SERUM_CREATININE => handleCreatinineInSerumUnit(quantity, _targetUnit)
      case UnitConceptEnum.URINE_CREATININE => handleCreatinineInUrineUnit(quantity, _targetUnit)
      case UnitConceptEnum.TRIGLYCERIDES => handleTriglyceridesUnit(quantity, _targetUnit)
      case UnitConceptEnum.SERUM_POTASSIUM => handlePotassiumInSerumUnit(quantity, _targetUnit)
      case _ => quantity
    }
  }

  /**
   * Convert Cholesterol unit to the target unit
   *
   * [mmol/L] = [mg/dL] / 38.67
   *
   * @param quantity Quantity to be converted
   * @param targetUnit Targeted unit
   * @return Converted quantity
   */
  private def handleCholesterolUnit(quantity: Quantity, targetUnit: String): Quantity = {
    if (quantity.unit.isDefined && quantity.value.isDefined) {
      val unit = quantity.unit.get
      if (unit.equalsIgnoreCase(ObservationUnitEnum.MMOL_L) && targetUnit.equalsIgnoreCase(ObservationUnitEnum.MG_DL)) {
        quantity.value = Option(BigDecimal(quantity.value.get * 38.67).setScale(2, BigDecimal.RoundingMode.HALF_UP).doubleValue)
        quantity.unit = Option(targetUnit)
      } else if (unit.equalsIgnoreCase(ObservationUnitEnum.MG_DL) && targetUnit.equalsIgnoreCase(ObservationUnitEnum.MMOL_L)) {
        quantity.value = Option(BigDecimal(quantity.value.get / 38.67).setScale(2, BigDecimal.RoundingMode.HALF_UP).doubleValue)
        quantity.unit = Option(targetUnit)
      }
    }
    quantity
  }

  /**
   * Convert HbA1c unit to the target unit
   *
   * [%] = 0.09148 * [mmol/mol Hb] + 2.152
   *
   * @param quantity Quantity to be converted
   * @param targetUnit Targeted unit
   * @return Converted quantity
   */
  private def handleHbA1CUnit(quantity: Quantity, targetUnit: String): Quantity = {
    if (quantity.unit.isDefined && quantity.value.isDefined) {
      val unit = quantity.unit.get
      if (unit.equalsIgnoreCase(ObservationUnitEnum.MMOL_MOL) && targetUnit.equalsIgnoreCase(ObservationUnitEnum.PERCENT)) {
        quantity.value = Option(BigDecimal(quantity.value.get * 0.09148 + 2.152).setScale(2, BigDecimal.RoundingMode.HALF_UP).doubleValue)
        quantity.unit = Option(targetUnit)
      } else if (unit.equalsIgnoreCase(ObservationUnitEnum.PERCENT) && targetUnit.equalsIgnoreCase(ObservationUnitEnum.MMOL_MOL)) {
        quantity.value = Option(BigDecimal((quantity.value.get - 2.152) / 0.09148).setScale(2, BigDecimal.RoundingMode.HALF_UP).doubleValue)
        quantity.unit = Option(targetUnit)
      }
    }
    quantity
  }

  /**
   * Convert Fasting Glucose unit to the target unit
   *
   * [mmol/L] = [mg/dL] x 0.0555
   *
   * @param quantity Quantity to be converted
   * @param targetUnit Targeted unit
   * @return Converted quantity
   */
  private def handleFastingGlucoseUnit(quantity: Quantity, targetUnit: String): Quantity = {
    if (quantity.unit.isDefined && quantity.value.isDefined) {
      val unit = quantity.unit.get
      if (unit.equalsIgnoreCase(ObservationUnitEnum.MMOL_L) && targetUnit.equalsIgnoreCase(ObservationUnitEnum.MG_DL)) {
        quantity.value = Option(BigDecimal(quantity.value.get / 0.0555).setScale(2, BigDecimal.RoundingMode.HALF_UP).doubleValue)
        quantity.unit = Option(targetUnit)
      } else if (unit.equalsIgnoreCase(ObservationUnitEnum.MG_DL) && targetUnit.equalsIgnoreCase(ObservationUnitEnum.MMOL_L)) {
        quantity.value = Option(BigDecimal(quantity.value.get * 0.0555).setScale(2, BigDecimal.RoundingMode.HALF_UP).doubleValue)
        quantity.unit = Option(targetUnit)
      }
    }
    quantity
  }

  /**
   * Convert Triglycerides unit to the target unit
   *
   * [mmol/L] = [mg/dL] / 88.57
   *
   * @param quantity Quantity to be converted
   * @param targetUnit Targeted unit
   * @return Converted quantity
   */
  private def handleTriglyceridesUnit(quantity: Quantity, targetUnit: String): Quantity = {
    if (quantity.unit.isDefined && quantity.value.isDefined) {
      val unit = quantity.unit.get
      if (unit.equalsIgnoreCase(ObservationUnitEnum.MMOL_L) && targetUnit.equalsIgnoreCase(ObservationUnitEnum.MG_DL)) {
        quantity.value = Option(BigDecimal(quantity.value.get * 88.57).setScale(2, BigDecimal.RoundingMode.HALF_UP).doubleValue)
        quantity.unit = Option(targetUnit)
      } else if (unit.equalsIgnoreCase(ObservationUnitEnum.MG_DL) && targetUnit.equalsIgnoreCase(ObservationUnitEnum.MMOL_L)) {
        quantity.value = Option(BigDecimal(quantity.value.get / 88.57).setScale(2, BigDecimal.RoundingMode.HALF_UP).doubleValue)
        quantity.unit = Option(targetUnit)
      }
    }
    quantity
  }

  /**
   * Convert ACR unit to the target unit
   *
   * [mg/g] = [µg/µmol] * 8.85
   *
   * [mg/g] = [mg/mg] * 1000
   *
   * [mg/mmol] = [mg/g] * 0.113
   *
   * @param quantity Quantity to be converted
   * @param targetUnit Targeted unit
   * @return Converted quantity
   */
  private def handleACRUnit(quantity: Quantity, targetUnit: String): Quantity = {
    if (quantity.unit.isDefined && quantity.value.isDefined && quantity.unit.get != targetUnit) {
      quantity.unit.get.toLowerCase(Locale.ENGLISH) match {
        case ObservationUnitEnum.MG_G =>
          targetUnit.toLowerCase(Locale.ENGLISH) match {
            case ObservationUnitEnum.MG_MMOL =>
              quantity.value = Option(BigDecimal(quantity.value.get * 0.113).setScale(2, BigDecimal.RoundingMode.HALF_UP).doubleValue)
              quantity.unit = Option(targetUnit)
            case ObservationUnitEnum.MG_MG =>
              quantity.value = Option(BigDecimal(quantity.value.get * 1000).setScale(2, BigDecimal.RoundingMode.HALF_UP).doubleValue)
              quantity.unit = Option(targetUnit)
            case ObservationUnitEnum.UG_UMOL =>
              quantity.value = Option(BigDecimal(quantity.value.get / 8.85).setScale(2, BigDecimal.RoundingMode.HALF_UP).doubleValue)
              quantity.unit = Option(targetUnit)
          }
        case ObservationUnitEnum.MG_MG =>
          quantity.value = Option(BigDecimal(quantity.value.get / 1000).setScale(2, BigDecimal.RoundingMode.HALF_UP).doubleValue)
          quantity.unit = Option(ObservationUnitEnum.MG_G)
          handleACRUnit(quantity, targetUnit)
        case ObservationUnitEnum.MG_MMOL =>
          quantity.value = Option(BigDecimal(quantity.value.get / 0.113).setScale(2, BigDecimal.RoundingMode.HALF_UP).doubleValue)
          quantity.unit = Option(ObservationUnitEnum.MG_G)
          handleACRUnit(quantity, targetUnit)
        case ObservationUnitEnum.UG_UMOL =>
          quantity.value = Option(BigDecimal(quantity.value.get * 8.85).setScale(2, BigDecimal.RoundingMode.HALF_UP).doubleValue)
          quantity.unit = Option(ObservationUnitEnum.MG_G)
          handleACRUnit(quantity, targetUnit)
      }
    }
    quantity
  }

  /**
   * Convert Creatinine in Serum unit to the target unit
   *
   * [µmol/L] = [mg/dL] * 88.4
   *
   * @param quantity Quantity to be converted
   * @param targetUnit Targeted unit
   * @return Converted quantity
   */
  private def handleCreatinineInSerumUnit(quantity: Quantity, targetUnit: String): Quantity = {
    if (quantity.unit.isDefined && quantity.value.isDefined) {
      val unit = quantity.unit.get
      if (unit.equalsIgnoreCase(ObservationUnitEnum.UMOL_L) && targetUnit.equalsIgnoreCase(ObservationUnitEnum.MG_DL)) {
        quantity.value = Option(BigDecimal(quantity.value.get / 88.4).setScale(2, BigDecimal.RoundingMode.HALF_UP).doubleValue)
        quantity.unit = Option(targetUnit)
      } else if (unit.equalsIgnoreCase(ObservationUnitEnum.MG_DL) && targetUnit.equalsIgnoreCase(ObservationUnitEnum.UMOL_L)) {
        quantity.value = Option(BigDecimal(quantity.value.get * 88.4).setScale(2, BigDecimal.RoundingMode.HALF_UP).doubleValue)
        quantity.unit = Option(targetUnit)
      }
    }
    quantity
  }

  /**
   * Convert Creatinine in Urine unit to the target unit
   *
   * [mmol/L] = [mg/dL] * 0.08840
   *
   * @param quantity Quantity to be converted
   * @param targetUnit Targeted unit
   * @return Converted quantity
   */
  private def handleCreatinineInUrineUnit(quantity: Quantity, targetUnit: String): Quantity = {
    if (quantity.unit.isDefined && quantity.value.isDefined) {
      val unit = quantity.unit.get
      if (unit.equalsIgnoreCase(ObservationUnitEnum.MMOL_L) && targetUnit.equalsIgnoreCase(ObservationUnitEnum.MG_DL)) {
        quantity.value = Option(BigDecimal(quantity.value.get / 0.08840).setScale(2, BigDecimal.RoundingMode.HALF_UP).doubleValue)
        quantity.unit = Option(targetUnit)
      } else if (unit.equalsIgnoreCase(ObservationUnitEnum.MG_DL) && targetUnit.equalsIgnoreCase(ObservationUnitEnum.MMOL_L)) {
        quantity.value = Option(BigDecimal(quantity.value.get * 0.08840).setScale(2, BigDecimal.RoundingMode.HALF_UP).doubleValue)
        quantity.unit = Option(targetUnit)
      }
    }
    quantity
  }

  /**
   * Convert Potassium in Urine unit to the target unit (Only change unit)
   *
   * [mmol/L] = [mEq/L] * 1
   *
   * @param quantity Quantity to be converted
   * @param targetUnit Targeted unit
   * @return Converted quantity
   */
  private def handlePotassiumInSerumUnit(quantity: Quantity, targetUnit: String): Quantity = {
    if (quantity.unit.isDefined && quantity.value.isDefined) {
      val unit = quantity.unit.get
      if (unit.equalsIgnoreCase(ObservationUnitEnum.MMOL_L) && targetUnit.equalsIgnoreCase(ObservationUnitEnum.MEQ_L)) {
        quantity.unit = Option(targetUnit)
      } else if (unit.equalsIgnoreCase(ObservationUnitEnum.MEQ_L) && targetUnit.equalsIgnoreCase(ObservationUnitEnum.MMOL_L)) {
        quantity.unit = Option(targetUnit)
      }
    }
    quantity
  }

}
