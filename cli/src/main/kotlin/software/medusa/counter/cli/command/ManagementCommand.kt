package software.medusa.counter.cli.command

import software.medusa.counter.cli.api.CounterApiClient
import software.medusa.counter.cli.auth.DevTokenProvider
import software.medusa.counter.cli.config.ConfigStore
import software.medusa.counter.cli.config.Environment

/**
 * Base for commands that talk to the counter API. Builds the CounterApiClient for the resolved
 * environment, wiring in the dev token provider (an optional bearer token — the backend runs no-op
 * auth for now), and runs [run] against it, closing the client afterward.
 */
abstract class ManagementCommand(name: String) : AppCommand(name = name) {
  final override fun run(environment: Environment, configStore: ConfigStore) {
    val tokenProvider = DevTokenProvider(configStore)
    CounterApiClient(environment.apiEndpoint, tokenProvider).use { run(it) }
  }

  abstract fun run(apiClient: CounterApiClient)
}
