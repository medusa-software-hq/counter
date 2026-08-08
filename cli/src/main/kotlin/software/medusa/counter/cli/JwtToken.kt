package software.medusa.counter.cli

import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * The claims we read out of a Google ID token *we already trust* — it came straight from Google's
 * token endpoint over TLS, so this is not a verification path (the API re-verifies the signature).
 * [parse] reads only the payload and never validates; any unreadable claim comes back null.
 */
data class JwtToken(val email: String?, val expiresAtEpochSec: Long?) {
  companion object {
    private val json = Json { ignoreUnknownKeys = true }
    private val urlDecoder: Base64.Decoder = Base64.getUrlDecoder()

    /** Reads the claims from [idToken]'s payload; a malformed token or claim yields nulls. */
    fun parse(idToken: String): JwtToken {
      val payload = runCatching { payload(idToken) }.getOrNull() ?: return JwtToken(null, null)
      return JwtToken(
          email = runCatching { payload["email"]?.jsonPrimitive?.content }.getOrNull(),
          expiresAtEpochSec = runCatching { payload["exp"]?.jsonPrimitive?.longOrNull }.getOrNull(),
      )
    }

    private fun payload(idToken: String): JsonObject {
      val parts = idToken.split(".")
      require(parts.size == 3) { "Not a JWT" }
      val decoded = urlDecoder.decode(parts[1])
      return json.parseToJsonElement(String(decoded, Charsets.UTF_8)) as JsonObject
    }
  }
}
