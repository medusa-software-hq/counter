package software.medusa.counter.cli

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import software.medusa.counter.cli.command.DecrementCommand
import software.medusa.counter.cli.command.GetCommand
import software.medusa.counter.cli.command.IncrementCommand
import software.medusa.counter.cli.command.LoginCommand
import software.medusa.counter.cli.command.LogoutCommand

class MainCommand : NoOpCliktCommand(name = "ms-counter") {
  override fun help(context: Context) = "Increment, decrement, and read the counter."
}

fun main(args: Array<String>) {
  // Each command is self-contained: it resolves the environment, opens its config, and (for API
  // commands) builds the authenticated client — see AppCommand / ManagementCommand.
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
