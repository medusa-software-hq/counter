package software.medusa.counter.cli.command

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.requireObject
import software.medusa.counter.cli.config.Environment

class GetCommand : CliktCommand(name = "get") {
  private val env by requireObject<Environment>()

  override fun help(context: Context) = "Print the current counter value."

  override fun run() = runCounter { env.apiClient().use { echo("Count: ${it.getCount()}") } }
}
