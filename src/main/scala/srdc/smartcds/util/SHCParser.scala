package srdc.smartcds.util

import com.nimbusds.jose.JWSObject
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jose.jwk.{ECKey, JWKSet}
import com.nimbusds.jose.util.Base64URL
import org.json4s.JValue
import srdc.smartcds.config.SmartCdsConfig
import srdc.smartcds.util.Json4sFormatter._

import java.net.URL
import java.util.zip.Inflater
import scala.util.Try

object SHCParser {

  /**
   * Extracts cards (payload) from the encoded SHC string
   * @param shc SHC string
   * @return
   */
  private def parse(shc: String) = {
    val Array(h, p, s) = shc.split("\\.").map(new Base64URL(_))
    val jws = new JWSObject(h, p, s)
    val card = inflate(jws.getPayload.toBytes).parseJson
    if (SmartCdsConfig.shcStrictSignatureVerification) {
      val issuer = (card \ "iss").extract[String]
      val jwkSet = JWKSet.load(new URL(issuer.stripSuffix("/") + "/.well-known/jwks"))
      val verifier = new ECDSAVerifier(jwkSet.getKeyByKeyId(jws.getHeader.getKeyID).asInstanceOf[ECKey].toPublicJWK)
      if (!JWSObject.parse(shc).verify(verifier)) {
        throw new Exception("SMART Health Card is not signed correctly!")
      }
    }
    card
  }

  /**
   * Extracts FHIR bundle from the Smart Health Card
   * @param shc
   * @return
   */
  def getBundle(shc: String): Option[JValue] = {
    Try(parse(shc) \ "vc" \ "credentialSubject" \ "fhirBundle").toOption
  }

  /**
   * Inflates the deflated data
   * @param data
   * @return
   */
  private def inflate(data: Array[Byte]) = {
    val inflater = new Inflater(true)
    inflater.setInput(data)
    val output = new StringBuilder()
    val buffer = new Array[Byte](data.length * 2)

    while (!inflater.finished()) {
      val count = inflater.inflate(buffer)
      output.append(new String(buffer, 0, count, "UTF-8"))
    }

    inflater.end()
    output.toString
  }

}
