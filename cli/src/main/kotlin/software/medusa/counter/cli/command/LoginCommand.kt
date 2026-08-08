package software.medusa.counter.cli.command

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.requireObject
import com.nimbusds.oauth2.sdk.auth.Secret
import com.nimbusds.oauth2.sdk.id.ClientID
import software.medusa.counter.cli.auth.GoogleOAuth
import software.medusa.counter.cli.auth.JwtToken
import software.medusa.counter.cli.auth.OAuth2TokenClient
import software.medusa.counter.cli.auth.OAuthException
import software.medusa.counter.cli.auth.SystemBrowserOpener
import software.medusa.counter.cli.auth.googleSignIn
import software.medusa.counter.cli.config.Credentials
import software.medusa.counter.cli.config.Environment

/** `login` — the loopback + PKCE browser sign-in; caches the refresh token for this environment. */
class LoginCommand : CliktCommand(name = "login") {
  private val env by requireObject<Environment>()

  override fun help(context: Context) =
      "Sign in with your medusa.software Google account and cache the session."

  override fun run() {
    val secret =
        env.oauthClientSecret
            ?: throw PrintMessage(
                "This CLI build has no OAuth client secret for ${env.label} and " +
                    "${env.oauthClientSecretEnvVar} is not set. Install a released build, or set " +
                    "that env var for a local build.",
                statusCode = 1,
                printError = true,
            )

    val clientId = ClientID(env.oauthClientId)
    val tokenClient = OAuth2TokenClient(GoogleOAuth.TOKEN_ENDPOINT, clientId, Secret(secret))
    val tokens =
        try {
          googleSignIn(
              GoogleOAuth.AUTH_ENDPOINT,
              clientId,
              tokenClient,
              SystemBrowserOpener,
              echo = { echo(it) },
          )
        } catch (e: OAuthException) {
          throw PrintMessage("Sign-in failed: ${e.message}", statusCode = 1, printError = true)
        }

    val refreshToken =
        tokens.refreshToken
            ?: throw PrintMessage(
                "Google did not return a refresh token, so the session can't be cached. Try again.",
                statusCode = 1,
                printError = true,
            )
    val email = JwtToken.parse(tokens.idToken).email ?: "unknown"
    env.configStore()
        .saveCredentials(Credentials(refreshToken, tokens.idToken, tokens.expiresAt, email))
    echo("Signed in as $email")
  }
}
