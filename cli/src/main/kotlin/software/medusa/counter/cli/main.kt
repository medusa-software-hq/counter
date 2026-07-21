package software.medusa.counter.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands

class MainCommand : NoOpCliktCommand(name = "ms-counter") {
  override fun help(context: Context) =
      "Increment, decrement, and read the counter, authenticated with your Google sign-in."
}

fun main(args: Array<String>) {
  MainCommand()
      .subcommands(
          LoginCommand(),
          LogoutCommand(),
          IncrementCommand(),
          DecrementCommand(),
          GetCommand(),
      )
      .main(args)
}

private fun apiClient(): CounterApiClient {
  val session = Session()
  return CounterApiClient(CounterConfig.apiBaseUrl, idTokenProvider = { session.currentIdToken() })
}

/** Turns the two expected failures into clean, actionable CLI errors. */
private inline fun <T> runCounter(block: () -> T): T =
    try {
      block()
    } catch (e: NotLoggedInException) {
      throw PrintMessage(e.message ?: "Not signed in.", statusCode = 1, printError = true)
    } catch (e: ApiException) {
      throw PrintMessage(e.message ?: "API error.", statusCode = 1, printError = true)
    }

/** `login` — the loopback + PKCE browser sign-in; caches the refresh token for later. */
class LoginCommand : CliktCommand(name = "login") {
  override fun help(context: Context) =
      "Sign in with your medusa.software Google account and cache the session."

  override fun run() {
    val secret =
        CounterConfig.clientSecret
            ?: throw PrintMessage(
                "This CLI build has no OAuth client secret and ${CounterConfig.CLIENT_SECRET_ENV} " +
                    "is not set. Install a released build, or set that env var for a local build.",
                statusCode = 1,
                printError = true,
            )

    val tokens =
        try {
          CounterOAuth(clientSecret = secret).login(echo = { echo(it) })
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
    val email = Jwt.email(tokens.idToken) ?: "unknown"
    saveCredentials(Credentials(refreshToken, tokens.idToken, tokens.expiresAtEpochSec, email))
    echo("Signed in as $email")
  }
}

/** `logout` — forget the cached session. */
class LogoutCommand : CliktCommand(name = "logout") {
  override fun help(context: Context) = "Forget the cached session on this machine."

  override fun run() {
    deleteCredentials()
    echo("Signed out.")
  }
}

class IncrementCommand : CliktCommand(name = "increment") {
  override fun help(context: Context) = "Increment the counter and print the new value."

  override fun run() = runCounter { echo("Count: ${apiClient().increment()}") }
}

class DecrementCommand : CliktCommand(name = "decrement") {
  override fun help(context: Context) = "Decrement the counter and print the new value."

  override fun run() = runCounter { echo("Count: ${apiClient().decrement()}") }
}

class GetCommand : CliktCommand(name = "get") {
  override fun help(context: Context) = "Print the current counter value."

  override fun run() = runCounter { echo("Count: ${apiClient().getCount()}") }
}
