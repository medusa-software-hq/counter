package software.medusa.counter.server

import com.linecorp.armeria.common.HttpHeaderNames
import com.linecorp.armeria.common.HttpRequest
import com.linecorp.armeria.common.HttpResponse
import com.linecorp.armeria.common.HttpStatus
import com.linecorp.armeria.common.ResponseHeaders
import com.linecorp.armeria.server.DecoratingHttpServiceFunction
import com.linecorp.armeria.server.HttpService
import com.linecorp.armeria.server.ServiceRequestContext
import com.linecorp.armeria.server.auth.AuthTokenExtractors
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.source.JWKSourceBuilder
import com.nimbusds.jose.proc.BadJOSEException
import com.nimbusds.jose.proc.JWSVerificationKeySelector
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jwt.JWTClaimNames
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import java.net.URI
import java.text.ParseException

// Google's OpenID Connect endpoints, from its OIDC discovery document
// (https://accounts.google.com/.well-known/openid-configuration):
//   jwks_uri = https://www.googleapis.com/oauth2/v3/certs
//   issuer   = https://accounts.google.com
// Pinned rather than fetched at runtime — these change very rarely.
// Docs: https://developers.google.com/identity/openid-connect/openid-connect#discovery
private const val googleAccountsHostname = "accounts.google.com"
private val googleJwksUri = URI("https://www.googleapis.com/oauth2/v3/certs").toURL()
// Google emits `iss` as either the bare host or the https URL — accept both.
private val googleIssuers = setOf("https://$googleAccountsHostname", googleAccountsHostname)

// OIDC "email" claim — not an RFC 7519 registered claim, so there's no JWTClaimNames constant.
private const val emailClaim = "email"
// Google Workspace "hd" (hosted-domain) claim — Google-specific, no library constant.
private const val hostedDomainClaim = "hd"

// Header Google IAP injects on a verified request, carrying its signed JWT assertion. Absent when
// IAP is not in front of the service (as today), which is exactly what keeps the Google-token path
// unchanged in production. https://cloud.google.com/iap/docs/signed-headers-howto
private const val iapAssertionHeaderName = "x-goog-iap-jwt-assertion"

/**
 * A dual-mode gate. When a request carries a non-empty [iapAssertionHeaderName] it is verified as a
 * Google IAP assertion (see [serveIapAssertion]); otherwise it is verified as a *Sign in with
 * Google* ID token (see [serveGoogleIdToken]) — the pre-IAP path, kept unchanged.
 *
 * This is step 1 of the IAP migration: the decorator understands IAP assertions now, while IAP is
 * still off, so enabling it later is a small reversible flip. With IAP off no assertion header is
 * present, so production behavior is identical to before. This gate only allows/denies — it does
 * not produce any identity object (deliberately postponed).
 *
 * The *Sign in with Google* path verifies a Google-issued ID token, accepts the configured OAuth
 * clients (matched against the token's `aud`), and restricts access to a single Google Workspace /
 * Cloud Identity domain via the `hd` (hosted-domain) claim. Returns 401 on failure.
 */
class GoogleIdTokenAuthDecorator(
    // Google OAuth client IDs; each value is matched against the token's `aud` claim.
    private val allowedClientIds: Set<String>,
    private val allowedDomain: String,
    // Verifier for the IAP branch, or null when no IAP audience is configured (the state today):
    // then any IAP assertion is rejected. Dormant until IAP is enabled in front of the API.
    private val iapVerifier: IapAssertionVerifier?,
    // Service-account emails allowed through IAP without an `hd` claim — today just the web
    // service's
    // SA, which hops to the API on the browser's behalf. Empty until the flip.
    private val trustedProxyEmails: Set<String>,
) : DecoratingHttpServiceFunction {
  companion object {
    private val logger = org.slf4j.LoggerFactory.getLogger(GoogleIdTokenAuthDecorator::class.java)

    // No usable credential was presented (missing/empty/non-Bearer). Per RFC 6750 §3.1,
    // a request that carries no token gets a bare challenge with no error code.
    private val missingCredential: HttpResponse
      get() =
          HttpResponse.of(
              ResponseHeaders.builder(HttpStatus.UNAUTHORIZED)
                  .add(HttpHeaderNames.WWW_AUTHENTICATE, "Bearer")
                  .build()
          )

    // A token was presented but rejected (unparseable / bad signature / bad claims / wrong
    // audience / wrong hosted-domain). RFC 6750 error="invalid_token" — deliberately one
    // coarse bucket so we don't leak which validation step failed.
    private val invalidToken: HttpResponse
      get() =
          HttpResponse.of(
              ResponseHeaders.builder(HttpStatus.UNAUTHORIZED)
                  .add(HttpHeaderNames.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"")
                  .build()
          )

    // IAP-branch denials. 403: the assertion is authentic but the principal is not permitted
    // (foreign hosted-domain). 401: no acceptable authenticated identity — the assertion failed
    // verification, no IAP audience is configured, or the principal is neither in-domain nor a
    // trusted proxy. Neither carries a Bearer challenge: IAP requests do not use bearer
    // credentials.
    private val forbidden: HttpResponse
      get() = HttpResponse.of(HttpStatus.FORBIDDEN)

    private val unauthorized: HttpResponse
      get() = HttpResponse.of(HttpStatus.UNAUTHORIZED)

    private fun extractIapAssertion(req: HttpRequest): String? =
        req.headers().get(iapAssertionHeaderName)?.takeIf { it.isNotEmpty() }

    private val jwtProcessor = run {
      val jwkSource =
          JWKSourceBuilder.create<SecurityContext>(googleJwksUri).refreshAheadCache(true).build()

      val keySelector = JWSVerificationKeySelector(JWSAlgorithm.RS256, jwkSource)

      val emptyClaimSet = JWTClaimsSet.Builder().build()

      // Audience is verified manually below: Nimbus's DefaultJWTClaimsVerifier can only exact-match
      // a
      // single audience, but we accept any of a set (any of our accepted OAuth clients).

      // Require the claims we actually rely on downstream:
      //   sub   → stable, unique user id
      //   email → the caller's identity
      //   iat / exp → issuance/expiry, so we only accept fresh, unexpired tokens
      val claimsVerifier =
          DefaultJWTClaimsVerifier<SecurityContext>(
              /* exactMatchClaims = */ emptyClaimSet,
              /* requiredClaims = */ setOf(
                  JWTClaimNames.SUBJECT,
                  emailClaim,
                  JWTClaimNames.ISSUED_AT,
                  JWTClaimNames.EXPIRATION_TIME,
              ),
          )

      DefaultJWTProcessor<SecurityContext>().apply {
        jwsKeySelector = keySelector
        jwtClaimsSetVerifier = claimsVerifier
      }
    }

    private fun extractBearerToken(req: HttpRequest): String? =
        AuthTokenExtractors.oAuth2().apply(req.headers())?.accessToken()?.takeIf { it.isNotEmpty() }
  }

  override fun serve(
      delegate: HttpService,
      ctx: ServiceRequestContext,
      req: HttpRequest,
  ): HttpResponse {
    val iapAssertion = extractIapAssertion(req)
    return if (iapAssertion != null) serveIapAssertion(delegate, ctx, req, iapAssertion)
    else serveGoogleIdToken(delegate, ctx, req)
  }

  // Verify an IAP assertion, then gate on the verified principal. Order matters: check `hd` before
  // the proxy allowlist, so a mis-set allowlist can never let a human (who always carries `hd`) be
  // treated as the trusted proxy SA and gain impersonation.
  private fun serveIapAssertion(
      delegate: HttpService,
      ctx: ServiceRequestContext,
      req: HttpRequest,
      assertion: String,
  ): HttpResponse {
    // No verifier == no IAP audience configured. Fail closed rather than trust an assertion we
    // cannot bind to this service.
    val verifier =
        iapVerifier
            ?: run {
              logger.error(
                  "Rejecting {} {}: IAP assertion presented but no IAP audience is configured",
                  req.method(),
                  req.path(),
              )
              return unauthorized
            }

    val claimsSet =
        verifier.verify(assertion)
            ?: run {
              logger.debug(
                  "Rejecting {} {}: IAP assertion failed verification",
                  req.method(),
                  req.path(),
              )
              return unauthorized
            }

    val hd = claimsSet.getStringClaim(hostedDomainClaim)
    if (hd != null) {
      // A direct human / CLI principal: allow only from our own Workspace domain.
      return if (hd == allowedDomain) delegate.serve(ctx, req) else forbidden
    }

    // No `hd`: the only legitimate case is the web service's SA hopping to the API on the browser's
    // behalf. Allow it iff its email is on the proxy allowlist.
    val email = claimsSet.getStringClaim(emailClaim)
    if (email != null && email in trustedProxyEmails) return delegate.serve(ctx, req)

    // An IAP principal the app does not recognize: no `hd`, not a trusted proxy. Log loudly.
    logger.error(
        "Rejecting {} {}: unrecognized IAP principal (email={}, no hd claim)",
        req.method(),
        req.path(),
        email,
    )
    return unauthorized
  }

  private fun serveGoogleIdToken(
      delegate: HttpService,
      ctx: ServiceRequestContext,
      req: HttpRequest,
  ): HttpResponse {
    val token = extractBearerToken(req)
    if (token == null) {
      logger.debug("Rejecting {} {}: no bearer credential presented", req.method(), req.path())
      return missingCredential
    }

    val claimsSet =
        try {
          jwtProcessor.process(token, null)
        } catch (_: ParseException) {
          // Malformed / non-JWT token.
          return invalidToken
        } catch (_: BadJOSEException) {
          // Bad signature or failed claims verification.
          return invalidToken
        }
    // Anything else (e.g. RemoteKeySourceException when Google's JWKS is unreachable) is NOT the
    // client's fault — let it propagate to a 500 rather than masquerade as a 401.

    // Verify issuer manually (nimbus claimsVerifier checks exp/required fields).
    if (claimsSet.issuer !in googleIssuers) return invalidToken

    // Accept a token minted by any of our accepted OAuth clients (its `aud` must match a configured
    // client id).
    if ((claimsSet.audience ?: emptyList()).none { it in allowedClientIds }) return invalidToken

    // Enforce hosted domain: restrict access to our organization's Workspace domain, so a token
    // with a valid signature and audience but from a foreign Workspace is still rejected.
    val hd = claimsSet.getStringClaim(hostedDomainClaim)
    if (hd != allowedDomain) return invalidToken

    return delegate.serve(ctx, req)
  }
}
