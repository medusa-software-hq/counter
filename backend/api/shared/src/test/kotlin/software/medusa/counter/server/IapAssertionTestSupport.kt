package software.medusa.counter.server

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.jwk.source.JWKSource
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.time.Instant
import java.util.Date

// Mints Google IAP-shaped ES256 assertions with a test key, and exposes the matching public JWKS so
// the verifier can be built without reaching IAP's network endpoint.
object IapAssertionTestSupport {
  const val audience = "/projects/123/global/backendServices/456"
  private const val iapIssuer = "https://cloud.google.com/iap"

  val signingKey: ECKey = ECKeyGenerator(Curve.P_256).keyID("test-key").generate()

  // A key never published in [jwkSource]; assertions signed with it must fail verification.
  val foreignKey: ECKey = ECKeyGenerator(Curve.P_256).keyID("foreign-key").generate()

  val jwkSource: JWKSource<SecurityContext> = ImmutableJWKSet(JWKSet(signingKey.toPublicJWK()))

  // A claims builder pre-filled with a valid issuer/audience/subject/timestamps plus the given
  // email
  // (and hd, when non-null). Tests build it as-is or override one field to craft a bad assertion.
  fun claims(email: String, hd: String?): JWTClaimsSet.Builder =
      JWTClaimsSet.Builder()
          .issuer(iapIssuer)
          .audience(audience)
          .subject("iap-subject-123")
          .claim("email", email)
          .apply { if (hd != null) claim("hd", hd) }
          .issueTime(Date.from(Instant.now()))
          .expirationTime(Date.from(Instant.now().plusSeconds(3600)))

  fun sign(key: ECKey, claims: JWTClaimsSet): String =
      SignedJWT(JWSHeader.Builder(JWSAlgorithm.ES256).keyID(key.keyID).build(), claims)
          .apply { sign(ECDSASigner(key)) }
          .serialize()
}
