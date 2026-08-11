package software.medusa.counter.cli.command

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import software.medusa.counter.cli.config.ConfigStore
import software.medusa.counter.cli.config.Credentials
import software.medusa.counter.cli.config.Environment

/**
 * `login` — a dev placeholder while real sign-in is being ported. Real identity (a Cognito device
 * flow) lands in a later issue; for now the backend runs no-op auth, so this optionally caches a
 * dev bearer token for this environment and otherwise does nothing.
 */
class LoginCommand : AppCommand(name = "login") {
  override fun help(context: Context) =
      "Cache a dev bearer token for this environment (real sign-in is not wired up yet)."

  private val token: String? by
      argument(name = "DEV_TOKEN", help = "Optional dev bearer token to cache.").optional()

  override fun run(environment: Environment, configStore: ConfigStore) {
    val devToken = token
    if (devToken == null) {
      echo(
          "Real sign-in is not wired up yet (a Cognito device flow lands in a later issue). " +
              "The backend accepts unauthenticated calls for now, so you can run commands " +
              "without logging in, or pass a dev token to cache: 'ms-counter login <DEV_TOKEN>'."
      )
      return
    }
    configStore.saveCredentials(Credentials(devToken))
    echo("Cached a dev token for the ${environment.label} environment.")
  }
}
