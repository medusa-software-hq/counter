package software.medusa.counter.server

import com.nimbusds.jwt.JWTClaimsSet
import java.time.Instant
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import software.medusa.counter.server.IapAssertionTestSupport.audience
import software.medusa.counter.server.IapAssertionTestSupport.claims
import software.medusa.counter.server.IapAssertionTestSupport.foreignKey
import software.medusa.counter.server.IapAssertionTestSupport.jwkSource
import software.medusa.counter.server.IapAssertionTestSupport.sign
import software.medusa.counter.server.IapAssertionTestSupport.signingKey

class IapAssertionVerifierTest {
  private val verifier = IapAssertionVerifier.build(jwkSource, audience)

  @Test
  fun `accepts a valid assertion`() {
    assertNotNull(
        verifier.verify(sign(signingKey, claims("user@example.com", "example.com").build()))
    )
  }

  @Test
  fun `rejects a wrong audience`() {
    assertNull(
        verifier.verify(
            sign(signingKey, claims("user@example.com", "example.com").audience("/other").build())
        )
    )
  }

  @Test
  fun `rejects a foreign issuer`() {
    assertNull(
        verifier.verify(
            sign(
                signingKey,
                claims("user@example.com", "example.com").issuer("https://evil.example").build(),
            )
        )
    )
  }

  @Test
  fun `rejects an expired assertion`() {
    assertNull(
        verifier.verify(
            sign(
                signingKey,
                claims("user@example.com", "example.com")
                    .issueTime(Date.from(Instant.now().minusSeconds(120)))
                    .expirationTime(Date.from(Instant.now().minusSeconds(60)))
                    .build(),
            )
        )
    )
  }

  @Test
  fun `rejects an assertion missing a required claim`() {
    // No email — a claim the gate relies on.
    assertNull(
        verifier.verify(
            sign(
                signingKey,
                JWTClaimsSet.Builder()
                    .issuer("https://cloud.google.com/iap")
                    .audience(audience)
                    .subject("s")
                    .issueTime(Date.from(Instant.now()))
                    .expirationTime(Date.from(Instant.now().plusSeconds(3600)))
                    .build(),
            )
        )
    )
  }

  @Test
  fun `rejects a signature from an unpublished key`() {
    assertNull(verifier.verify(sign(foreignKey, claims("user@example.com", "example.com").build())))
  }

  @Test
  fun `rejects a malformed assertion`() {
    assertNull(verifier.verify("not-a-jwt"))
  }
}
