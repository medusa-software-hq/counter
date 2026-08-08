package software.medusa.counter.cli.command

import com.github.ajalt.clikt.core.Context
import software.medusa.counter.cli.api.CounterApiClient

class IncrementCommand : ManagementCommand(name = "increment") {
  override fun help(context: Context) = "Increment the counter and print the new value."

  override fun run(apiClient: CounterApiClient) {
    echo("Count: ${apiClient.increment()}")
  }
}
