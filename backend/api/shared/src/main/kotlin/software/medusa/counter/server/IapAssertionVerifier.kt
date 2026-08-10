package software.medusa.counter.server

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.jwk.source.JWKSourceBuilder
import com.nimbusds.jose.proc.BadJOSEException
import com.nimbusds.jose.proc.JWSVerificationKeySelector
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.JWTClaimNames
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import java.net.URI
import java.text.ParseException

// Google IAP's signing keys and issuer, from IAP's docs:
//   jwks_uri = https://www.gstatic.com/iap/verify/public_key-jwk  (EC / ES256 keys)
//   issuer   = https://cloud.google.com/iap
// A distinct JWKS/issuer/algorithm from Sign-in-with-Google (RS256, accounts.google.com), hence a
// distinct processor. https://cloud.google.com/iap/docs/signed-headers-howto
private val iapJwksUri = URI("https://www.gstatic.com/iap/verify/public_key-jwk").toURL()
private const val iapIssuer = "https://cloud.google.com/iap"

// OIDC "email" claim — not an RFC 7519 registered claim, so there's no JWTClaimNames constant.
private const val emailClaim = "email"

/**
 * Verifies a Google IAP JWT assertion (the `x-goog-iap-jwt-assertion` header): ES256 signature
 * against IAP's JWKS, issuer `https://cloud.google.com/iap`, and an exact-match audience. Returns
 * the verified claims, or `null` if the assertion is missing/malformed, unsigned by IAP, expired,
 * or fails issuer/audience verification — it fails closed and never throws for a bad assertion.
 *
 * The JWK source is injected so tests can supply a static in-memory JWKS instead of reaching IAP's
 * network endpoint; production uses [build] to fetch and cache the live keys.
 */
class IapAssertionVerifier(private val processor: ConfigurableJWTProcessor<SecurityContext>) {
  companion object {
    /** Production verifier: fetches and caches IAP's public JWKS over the network. */
    fun build(audience: String): IapAssertionVerifier =
        build(
            JWKSourceBuilder.create<SecurityContext>(iapJwksUri).refreshAheadCache(true).build(),
            audience,
        )

    /** Verifier over an injected JWK source — lets tests supply an in-memory JWKS. */
    fun build(jwkSource: JWKSource<SecurityContext>, audience: String): IapAssertionVerifier {
      val keySelector = JWSVerificationKeySelector(JWSAlgorithm.ES256, jwkSource)

      // Exact-match iss and aud (IAP mints a single audience per resource), and require the claims
      // the gate relies on: sub (stable id), email (the proxy allowlist branch), iat/exp
      // (freshness).
      val claimsVerifier =
          DefaultJWTClaimsVerifier<SecurityContext>(
              /* exactMatchClaims = */ JWTClaimsSet.Builder()
                  .issuer(iapIssuer)
                  .audience(audience)
                  .build(),
              /* requiredClaims = */ setOf(
                  JWTClaimNames.SUBJECT,
                  emailClaim,
                  JWTClaimNames.ISSUED_AT,
                  JWTClaimNames.EXPIRATION_TIME,
              ),
          )

      val processor =
          DefaultJWTProcessor<SecurityContext>().apply {
            jwsKeySelector = keySelector
            jwtClaimsSetVerifier = claimsVerifier
          }
      return IapAssertionVerifier(processor)
    }
  }

  fun verify(assertion: String): JWTClaimsSet? =
      try {
        processor.process(assertion, null)
      } catch (_: ParseException) {
        // Malformed / non-JWT assertion.
        null
      } catch (_: BadJOSEException) {
        // Bad signature or failed claims verification (issuer / audience / expiry / required).
        null
      }
  // Anything else (e.g. RemoteKeySourceException when IAP's JWKS is unreachable) is NOT the
  // client's fault — let it propagate rather than masquerade as a rejected assertion.
}
