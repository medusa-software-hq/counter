package software.medusa.counter.cli.command

import com.github.ajalt.clikt.core.Context
import software.medusa.counter.cli.api.CounterApiClient

class GetCommand : ManagementCommand(name = "get") {
  override fun help(context: Context) = "Print the current counter value."

  override fun run(apiClient: CounterApiClient) {
    echo("Count: ${apiClient.getCount()}")
  }
}
