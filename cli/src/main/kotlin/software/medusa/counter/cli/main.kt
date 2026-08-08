package software.medusa.counter.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.obj
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.core.subcommands
import com.nimbusds.oauth2.sdk.auth.Secret
import com.nimbusds.oauth2.sdk.id.ClientID
import java.nio.file.Path
import kotlin.system.exitProcess

class MainCommand : NoOpCliktCommand(name = "ms-counter") {
  override fun help(context: Context) =
      "Increment, decrement, and read the counter, authenticated with your Google sign-in."
}

fun main(args: Array<String>) {
  // Read-once composition root: resolve the environment from COUNTER_ENVIRONMENT exactly here, then
  // inject it via the Clikt context so no command reads that variable again.
  val environment =
      try {
        Environment.current()
      } catch (e: EnvironmentSelectionException) {
        System.err.println(e.message)
        exitProcess(2)
      }

  // Non-prod sessions announce themselves on stderr (partitioned state dirs prevent *state* mixing;
  // this prevents *human* mixing). Dim when stderr is a terminal; plain otherwise.
  environment.marker?.let { System.err.println(dimmedForStderr(it)) }

  MainCommand()
      .context { obj = environment }
      .subcommands(
          LoginCommand(),
          LogoutCommand(),
          IncrementCommand(),
          DecrementCommand(),
          GetCommand(),
      )
      .main(args)
}

/**
 * An authenticated client for this environment (its endpoint + its cached, silently-refreshed
 * token).
 */
/** [text] wrapped in ANSI dim, but only when stderr is an interactive terminal (else plain). */
private fun dimmedForStderr(text: String): String =
    if (System.console() != null) "[2m$text[22m" else text

private fun counterConfigBase(): Path =
    ConfigBaseDir.resolve(System.getenv("XDG_CONFIG_HOME"), System.getProperty("user.home"))

private fun Environment.configStore(): ConfigStore =
    ConfigStore(resolveConfigDirPath(counterConfigBase()))

private fun Environment.apiClient(): CounterApiClient =
    CounterApiClient(apiEndpoint, Session(configStore(), refresher = DefaultTokenRefresher(this)))

/** Turns the two expected failures into clean, actionable CLI errors. */
private inline fun <T> runCounter(block: () -> T): T =
    try {
      block()
    } catch (e: NotLoggedInException) {
      throw PrintMessage(e.message ?: "Not signed in.", statusCode = 1, printError = true)
    } catch (e: ApiException) {
      throw PrintMessage(e.message ?: "API error.", statusCode = 1, printError = true)
    }

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

/** `logout` — forget the cached session for this environment. */
class LogoutCommand : CliktCommand(name = "logout") {
  private val env by requireObject<Environment>()

  override fun help(context: Context) = "Forget the cached session on this machine."

  override fun run() {
    env.configStore().deleteCredentials()
    echo("Signed out.")
  }
}

class IncrementCommand : CliktCommand(name = "increment") {
  private val env by requireObject<Environment>()

  override fun help(context: Context) = "Increment the counter and print the new value."

  override fun run() = runCounter { env.apiClient().use { echo("Count: ${it.increment()}") } }
}

class DecrementCommand : CliktCommand(name = "decrement") {
  private val env by requireObject<Environment>()

  override fun help(context: Context) = "Decrement the counter and print the new value."

  override fun run() = runCounter { env.apiClient().use { echo("Count: ${it.decrement()}") } }
}

class GetCommand : CliktCommand(name = "get") {
  private val env by requireObject<Environment>()

  override fun help(context: Context) = "Print the current counter value."

  override fun run() = runCounter { env.apiClient().use { echo("Count: ${it.getCount()}") } }
}
