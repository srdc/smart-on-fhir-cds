package srdc.smartcds.util

import srdc.smartcds.config.SmartCdsConfig
import srdc.smartcds.model.fhir.{Bundle, ValueSet}
import org.json4s.JNothing
import org.json4s.JsonAST.{JObject, JString}
import srdc.smartcds.util.Json4sFormatter.parseFromJson
import srdc.smartcds.util.Json4sFormatter._

import scala.io.Source

object ValueSetUtil {

  /** Concept ID -> (system, code, designation) map */
  var conceptMap: Map[String, List[(String, String, Map[String, String])]] = Map()
  /** ValueSet ID -> ValueSet map */
  var valueSetMap: Map[String, JObject] = Map()

  /** Read bundles from files */
  {
    try {
      val bundles = new java.io.File(SmartCdsConfig.bundlesPath.get).listFiles.filter(_.getName.endsWith(".json"))
      for (bundlePath <- bundles) {
        val source = Source.fromFile(bundlePath)(scala.io.Codec("UTF-8"))
        val bundleObject = source.mkString.parseJson.asInstanceOf[JObject]
        val bundle = bundleObject.extract[Bundle]
        bundle.entry.foreach(bundleEntry => {
          val valueSet = bundleEntry.resource.extract[ValueSet]
          valueSet.compose.include.get.foreach(e => {
            println(e)
            val list = e.concept.get.map(coding => (e.system.get, coding.code, coding.designation.map(_.map(designation => (designation.language.get -> designation.value.get)).toMap).getOrElse(Map.empty))).toList
            conceptMap += (valueSet.id.get -> list)
          })
        })
      }
    } catch {
      case e: Exception =>
        println(s"Error while reading bundles", e)
    }
  }

  /** Read value sets from files */
  {
    try {
      val valuesets = new java.io.File(SmartCdsConfig.valueSetsPath.get).listFiles.filter(_.getName.endsWith(".json"))
      for (valuesetPath <- valuesets) {
        val source = Source.fromFile(valuesetPath)(scala.io.Codec("UTF-8"))
        val vsObject = source.mkString.parseJson.asInstanceOf[JObject]
        valueSetMap += ((vsObject \ "id").extract[String] -> vsObject)
      }
    } catch {
      case e: Exception =>
        println(s"Error while reading valuesets", e)
    }
  }

  /**
   * @param conceptId ID of the concept defined in bundles
   * @return (system, code) pair
   */
  def getConceptSystemAndCode(conceptId: String): List[(String, String)] = {
    try {
      if (conceptMap.apply(conceptId) != null) {
        conceptMap.apply(conceptId).map(coding => (coding._1, coding._2))
      } else {
        List(("",""))
      }
    } catch {
      case e: Exception =>
        println(s"Error while searching conceptId: $conceptId", e)
        List(("",""))
    }
  }

  /**
   * @param conceptId ID of the concept defined in bundles
   * @return (system, code, display)
   */
  def getConceptSystemCodeAndDisplay(conceptId: String, lang: String): List[(String, String, String)] = {
    try {
      if (conceptMap.contains(conceptId)) {
        conceptMap.apply(conceptId).map(coding => (coding._1, coding._2, coding._3.getOrElse(lang, "")))
      } else {
        List(("","", ""))
      }
    } catch {
      case e: Exception =>
        println(s"Error while searching conceptId: $conceptId", e)
        List(("","", ""))
    }
  }

  /**
   * @param id ValueSet ID
   * @return JObject(system, code, display)
   */
  def getConceptsFromValueSet(id: String, lang: Option[String] = None): Seq[JObject] = {
    try {
      valueSetMap(id).extract[ValueSet].compose.include.get.flatMap(include => include.concept.get.map(concept => {
        val display = concept.designation.flatMap(_.find(designation => designation.language.contains(lang.getOrElse("en"))).flatMap(_.value))
          .getOrElse(concept.display.getOrElse(""))
        JObject(
          "system" -> JString(include.system.get),
          "code" -> JString(concept.code),
          "display" -> (if (display.nonEmpty) JString(display) else JNothing)
        )
      }))
    } catch {
      case e: Exception =>
        println(s"Cannot read concepts from valueset $id")
        Seq.empty
    }
  }

}
