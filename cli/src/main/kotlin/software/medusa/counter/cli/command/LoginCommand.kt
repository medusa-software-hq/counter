package software.medusa.counter.cli.command

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.PrintMessage
import software.medusa.counter.cli.auth.GoogleOAuth
import software.medusa.counter.cli.auth.JwtToken
import software.medusa.counter.cli.auth.OAuth2TokenClient
import software.medusa.counter.cli.auth.OAuthException
import software.medusa.counter.cli.auth.SystemBrowserOpener
import software.medusa.counter.cli.auth.googleSignIn
import software.medusa.counter.cli.config.ConfigStore
import software.medusa.counter.cli.config.Credentials
import software.medusa.counter.cli.config.Environment

/** `login` — the loopback + PKCE browser sign-in; caches the refresh token for this environment. */
class LoginCommand : AppCommand(name = "login") {
  override fun help(context: Context) =
      "Sign in with your medusa.software Google account and cache the session."

  override fun run(environment: Environment, configStore: ConfigStore) {
    val clientId = environment.oauthClientId
    val tokenClient =
        OAuth2TokenClient(GoogleOAuth.TOKEN_ENDPOINT, clientId, environment.oauthClientSecret)
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
    configStore.saveCredentials(Credentials(refreshToken, tokens.idToken, tokens.expiresAt, email))
    echo("Signed in as $email")
  }
}
