package software.medusa.counter.cli

import com.nimbusds.oauth2.sdk.AuthorizationResponse
import com.nimbusds.oauth2.sdk.ResponseType
import com.nimbusds.oauth2.sdk.Scope
import com.nimbusds.oauth2.sdk.id.ClientID
import com.nimbusds.oauth2.sdk.id.State
import com.nimbusds.oauth2.sdk.pkce.CodeChallengeMethod
import com.nimbusds.oauth2.sdk.pkce.CodeVerifier
import com.nimbusds.openid.connect.sdk.AuthenticationRequest
import java.net.URI
import java.time.Duration

/** How long the loopback receiver waits for Google's browser redirect before giving up. */
private val CALLBACK_TIMEOUT: Duration = Duration.ofMinutes(5)

/**
 * The interactive Google sign-in: authorization-code + PKCE over a localhost loopback (RFC 8252).
 * Opens the browser to Google's consent screen, catches the redirect, and exchanges the code for
 * tokens via [tokenClient]. The OAuth/OIDC protocol (auth request, PKCE, callback parsing) is the
 * Nimbus SDK's; only the loopback and the browser launch are ours. [echo] reports progress.
 */
fun googleSignIn(
    authEndpoint: URI,
    clientId: ClientID,
    tokenClient: OAuth2TokenClient,
    browserOpener: BrowserOpener,
    echo: (String) -> Unit,
): TokenSet {
  val codeVerifier = CodeVerifier()
  val state = State()
  LoopbackReceiver().use { receiver ->
    val authUrl =
        googleAuthorizationUrl(authEndpoint, clientId, receiver.redirectUri, codeVerifier, state)
    echo("Opening your browser to sign in…")
    if (!browserOpener.open(authUrl)) {
      echo("Couldn't open a browser automatically. Open this URL to continue:\n$authUrl")
    }
    val response = AuthorizationResponse.parse(receiver.awaitCallback(CALLBACK_TIMEOUT))
    if (!response.indicatesSuccess()) {
      val error = response.toErrorResponse().errorObject
      throw OAuthException(error.code ?: "oauth_error", error.description)
    }
    val success = response.toSuccessResponse()
    if (success.state != state) {
      throw OAuthException("state_mismatch", "OAuth state did not match; aborting.")
    }
    val code =
        success.authorizationCode
            ?: throw OAuthException("no_code", "No authorization code returned.")
    return tokenClient.exchangeAuthorizationCode(code, codeVerifier, receiver.redirectUri)
  }
}

private fun googleAuthorizationUrl(
    authEndpoint: URI,
    clientId: ClientID,
    redirectUri: URI,
    codeVerifier: CodeVerifier,
    state: State,
): URI =
    AuthenticationRequest.Builder(
            ResponseType("code"),
            Scope("openid", "email"),
            clientId,
            redirectUri,
        )
        .endpointURI(authEndpoint)
        .state(state)
        .codeChallenge(codeVerifier, CodeChallengeMethod.S256)
        // Ask for a refresh token, and force the consent screen so we reliably get one.
        .customParameter("access_type", "offline")
        .customParameter("prompt", "consent")
        .build()
        .toURI()
