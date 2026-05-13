package srdc.smartcds.cds.client

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.unmarshalling.Unmarshal
import org.json4s._
import org.json4s.jackson.JsonMethods.parse
import org.json4s.jackson.Serialization.write
import org.slf4j.LoggerFactory
import srdc.smartcds.config.LlmConfig
import srdc.smartcds.model.llm._
import srdc.smartcds.model.llm.LlmJsonSupport._

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.control.NonFatal
import akka.stream.{Materializer, SystemMaterializer}


object LLMClient {

  private val logger = LoggerFactory.getLogger(getClass)

  implicit private val formats: Formats = DefaultFormats
  implicit def mat(implicit system: ActorSystem) =
    SystemMaterializer(system).materializer

  @volatile private var currentAuth: Option[LoginResponse] = None
  @volatile private var inFlightLogin: Option[Future[LoginResponse]] = None
  private val authLock = new AnyRef

  /**
   * Logs in to the LLM server and updates the cached auth token.
   * Concurrent callers share the same in-flight login request.
   */
  def login()(implicit system: ActorSystem, ec: ExecutionContext): Future[LoginResponse] = {
    authLock.synchronized {
      inFlightLogin match {
        case Some(existing) => existing
        case None =>
          val started = doLogin()
          inFlightLogin = Some(started)

          started.onComplete { result =>
            authLock.synchronized {
              inFlightLogin = None
              result.foreach(auth => currentAuth = Some(auth))
            }
          }

          started
      }
    }
  }

  /**
   * Calls POST {baseUrl}/shap_explain
   */
  def shapExplain(sessionId: Option[String] = None, prompt: Option[JObject] = None)(
    implicit system: ActorSystem,
    ec: ExecutionContext,
    mat: Materializer
  ): Future[ExplainResponse] = {
    for {
      auth <- ensureValidAuth()
      requestJson = write(InitialExplainRequest(session_id = sessionId, user_text = prompt))
      request = HttpRequest(
        method = HttpMethods.POST,
        uri = s"${LlmConfig.baseUrl}/shap_explain_plot",
        headers = List(Authorization(OAuth2BearerToken(auth.access_token))),
        entity = HttpEntity(ContentTypes.`application/json`, requestJson)
      )
      response <- Http().singleRequest(request)
      result <- handleJsonResponse[ExplainResponse](response, "shapExplain")
    } yield result
  }

  /**
   * Calls POST {baseUrl}/shap_explain
   */
  def ask(sessionId: String, prompt: String)(
    implicit system: ActorSystem,
    ec: ExecutionContext,
    mat: Materializer
  ): Future[ExplainResponse] = {
    for {
      auth <- ensureValidAuth()
      requestJson = write(ExplainRequest(session_id = Some(sessionId), user_text = Some(prompt)))
      request = HttpRequest(
        method = HttpMethods.POST,
        uri = s"${LlmConfig.baseUrl}/shap_explain_plot",
        headers = List(Authorization(OAuth2BearerToken(auth.access_token))),
        entity = HttpEntity(ContentTypes.`application/json`, requestJson)
      )
      response <- Http().singleRequest(request)
      result <- handleJsonResponse[ExplainResponse](response, "chat__direct")
    } yield result
  }

  /**
   * Clears the cached token, useful if the caller wants to force a fresh login.
   */
  def clearAuthCache(): Unit = authLock.synchronized {
    currentAuth = None
    inFlightLogin = None
  }

  private def ensureValidAuth()(
    implicit system: ActorSystem,
    ec: ExecutionContext
  ): Future[LoginResponse] = {
    currentAuth match {
      case Some(auth) if !isTokenExpired(auth.access_token) =>
        Future.successful(auth)
      case _ =>
        login()
    }
  }

  private def doLogin()(
    implicit system: ActorSystem,
    ec: ExecutionContext
  ): Future[LoginResponse] = {
    val requestJson = write(LoginRequest(LlmConfig.clientId, LlmConfig.clientSecret))

    val request = HttpRequest(
      method = HttpMethods.POST,
      uri = s"${LlmConfig.baseUrl}/auth/client_login",
      entity = HttpEntity(ContentTypes.`application/json`, requestJson)
    )

    for {
      response <- Http().singleRequest(request)
      auth <- handleJsonResponse[LoginResponse](response, "login")
    } yield auth
  }

  private def handleJsonResponse[T <: AnyRef](
                                               response: HttpResponse,
                                               operationName: String
                                             )(implicit mf: Manifest[T], ec: ExecutionContext, mat: Materializer): Future[T] = {

    Unmarshal(response.entity).to[String].flatMap { body =>
      response.status match {
        case StatusCodes.OK =>
          try {
            Future.successful(parse(body).extract[T])
          } catch {
            case NonFatal(e) =>
              logger.error(s"[LLM] $operationName JSON parsing failed. body={}", body, e)
              Future.failed(
                new RuntimeException(s"[LLM] $operationName JSON parsing failed: ${e.getMessage}", e)
              )
          }

        case status =>
          logger.warn(s"[LLM] {} failed: {} body={}", operationName, status, body)
          Future.failed(
            new RuntimeException(s"[LLM] $operationName failed: $status body=$body")
          )
      }
    }
  }

  /**
   * Returns true if:
   *  - token is missing/blank
   *  - token is not a JWT (can't parse)
   *  - token exp is missing or not a number
   *  - token is expired (with optional skew)
   */
  def isTokenExpired(jwt: String, skewSeconds: Long = 30L): Boolean = {
    if (jwt == null || jwt.trim.isEmpty) return true

    try {
      val parts = jwt.split("\\.")
      if (parts.length < 2) return true

      val payloadJson =
        new String(base64UrlDecode(parts(1)), StandardCharsets.UTF_8)

      val expOpt: Option[Long] =
        (parse(payloadJson) \ "exp") match {
          case JInt(v)     => Some(v.toLong)
          case JDouble(v)  => Some(v.toLong)
          case JDecimal(v) => Some(v.toLong)
          case JString(s)  => scala.util.Try(s.toLong).toOption
          case _           => None
        }

      expOpt match {
        case Some(expSeconds) =>
          Instant.now().getEpochSecond >= (expSeconds - skewSeconds)
        case None =>
          true
      }
    } catch {
      case NonFatal(_) => true
    }
  }

  private def base64UrlDecode(value: String): Array[Byte] = {
    val normalized = value.replace('-', '+').replace('_', '/')
    val padded = normalized.length % 4 match {
      case 0 => normalized
      case 2 => normalized + "=="
      case 3 => normalized + "="
      case _ => normalized
    }

    Base64.getDecoder.decode(padded)
  }
}