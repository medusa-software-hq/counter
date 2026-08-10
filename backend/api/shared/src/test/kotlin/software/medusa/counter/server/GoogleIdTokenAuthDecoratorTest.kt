package software.medusa.counter.server

import com.linecorp.armeria.common.HttpMethod
import com.linecorp.armeria.common.HttpRequest
import com.linecorp.armeria.common.HttpResponse
import com.linecorp.armeria.common.HttpStatus
import com.linecorp.armeria.common.RequestHeaders
import com.linecorp.armeria.server.HttpService
import com.linecorp.armeria.server.ServiceRequestContext
import kotlin.test.Test
import kotlin.test.assertEquals
import software.medusa.counter.server.IapAssertionTestSupport.audience
import software.medusa.counter.server.IapAssertionTestSupport.claims
import software.medusa.counter.server.IapAssertionTestSupport.jwkSource
import software.medusa.counter.server.IapAssertionTestSupport.sign
import software.medusa.counter.server.IapAssertionTestSupport.signingKey

private const val assertionHeader = "x-goog-iap-jwt-assertion"
private const val proxyEmail = "spa-web@project.iam.gserviceaccount.com"

class GoogleIdTokenAuthDecoratorTest {
  private val verifier = IapAssertionVerifier.build(jwkSource, audience)

  private val okDelegate =
      object : HttpService {
        override fun serve(ctx: ServiceRequestContext, req: HttpRequest): HttpResponse =
            HttpResponse.of(HttpStatus.OK)
      }

  private fun gate(iapVerifier: IapAssertionVerifier?, headers: RequestHeaders): HttpStatus {
    val decorator =
        GoogleIdTokenAuthDecorator(
            allowedClientIds = setOf("web-client"),
            allowedDomain = "example.com",
            iapVerifier = iapVerifier,
            trustedProxyEmails = setOf(proxyEmail),
        )
    val req = HttpRequest.of(headers)
    return decorator
        .serve(okDelegate, ServiceRequestContext.of(req), req)
        .aggregate()
        .join()
        .status()
  }

  private fun withAssertion(jwt: String): RequestHeaders =
      RequestHeaders.of(HttpMethod.POST, "/counter", assertionHeader, jwt)

  @Test
  fun `in-domain human is allowed`() {
    val jwt = sign(signingKey, claims("user@example.com", "example.com").build())
    assertEquals(HttpStatus.OK, gate(verifier, withAssertion(jwt)))
  }

  @Test
  fun `foreign hosted-domain is forbidden`() {
    val jwt = sign(signingKey, claims("user@evil.example", "evil.example").build())
    assertEquals(HttpStatus.FORBIDDEN, gate(verifier, withAssertion(jwt)))
  }

  @Test
  fun `hd is checked before the proxy allowlist`() {
    // A principal carrying hd is a human even if its email is on the proxy allowlist: a foreign
    // human must be forbidden, never quietly accepted as the trusted proxy.
    val jwt = sign(signingKey, claims(proxyEmail, "evil.example").build())
    assertEquals(HttpStatus.FORBIDDEN, gate(verifier, withAssertion(jwt)))
  }

  @Test
  fun `trusted proxy without hd is allowed`() {
    val jwt = sign(signingKey, claims(proxyEmail, null).build())
    assertEquals(HttpStatus.OK, gate(verifier, withAssertion(jwt)))
  }

  @Test
  fun `unrecognized IAP principal is unauthorized`() {
    val jwt = sign(signingKey, claims("stranger@other.iam.gserviceaccount.com", null).build())
    assertEquals(HttpStatus.UNAUTHORIZED, gate(verifier, withAssertion(jwt)))
  }

  @Test
  fun `assertion with no verifier configured is unauthorized`() {
    val jwt = sign(signingKey, claims("user@example.com", "example.com").build())
    assertEquals(HttpStatus.UNAUTHORIZED, gate(null, withAssertion(jwt)))
  }

  @Test
  fun `no assertion falls through to the Google-token path`() {
    // No IAP header and no bearer credential -> the Google path's missing-credential 401.
    assertEquals(
        HttpStatus.UNAUTHORIZED,
        gate(verifier, RequestHeaders.of(HttpMethod.POST, "/counter")),
    )
  }
}
