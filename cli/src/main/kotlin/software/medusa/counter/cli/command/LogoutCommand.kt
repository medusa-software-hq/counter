package software.medusa.counter.cli.command

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.requireObject
import software.medusa.counter.cli.config.Environment

/** `logout` — forget the cached session for this environment. */
class LogoutCommand : CliktCommand(name = "logout") {
  private val env by requireObject<Environment>()

  override fun help(context: Context) = "Forget the cached session on this machine."

  override fun run() {
    env.configStore().deleteCredentials()
    echo("Signed out.")
  }
}
