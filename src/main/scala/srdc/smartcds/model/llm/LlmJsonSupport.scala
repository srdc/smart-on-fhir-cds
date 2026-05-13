package srdc.smartcds.model.llm

import org.json4s.DefaultFormats

/**
 * Keep JSON (de)serialization formats in one place for LLM models.
 * Clients/services can `import srdc.smartcds.model.llm.LlmJsonSupport._`
 */
object LlmJsonSupport {
  implicit val formats: DefaultFormats.type = DefaultFormats
}
