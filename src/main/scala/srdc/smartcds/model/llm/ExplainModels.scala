package srdc.smartcds.model.llm

import org.json4s.JObject

case class InitialExplainRequest(
                           session_id: Option[String],
                           user_text: Option[JObject]
                         )

case class ExplainRequest(
                           session_id: Option[String],
                           user_text: Option[String]
                         )

case class Explanation(
                      plot_overview: Option[String],
                      one_sentence_summary: Option[String],
                      what_increases_prediction: Option[List[String]],
                      what_decreases_prediction: Option[List[String]],
                      what_this_does_not_mean: Option[String],
                      confidence_and_caveats: Option[List[String]]
                      )

case class ExplainResponse(
                            session_id: String,
                            text: String,
                            suggested_questions: List[String],
                            is_new_session: Boolean,
                            explanation: Option[Explanation]
                          )
