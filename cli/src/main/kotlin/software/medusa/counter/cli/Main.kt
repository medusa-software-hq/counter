package software.medusa.counter.cli

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.obj
import com.github.ajalt.clikt.core.subcommands
import kotlin.system.exitProcess
import software.medusa.counter.cli.command.DecrementCommand
import software.medusa.counter.cli.command.GetCommand
import software.medusa.counter.cli.command.IncrementCommand
import software.medusa.counter.cli.command.LoginCommand
import software.medusa.counter.cli.command.LogoutCommand
import software.medusa.counter.cli.config.Environment
import software.medusa.counter.cli.config.EnvironmentSelectionException

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

/** [text] wrapped in ANSI dim, but only when stderr is an interactive terminal (else plain). */
private fun dimmedForStderr(text: String): String =
    if (System.console() != null) "[2m$text[22m" else text
