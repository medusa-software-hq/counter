package software.medusa.counter.cli.command

import kotlin.time.Clock
import software.medusa.counter.cli.api.CounterApiClient
import software.medusa.counter.cli.auth.ConfigIdTokenProvider
import software.medusa.counter.cli.auth.DefaultTokenRefresher
import software.medusa.counter.cli.auth.GoogleOAuth
import software.medusa.counter.cli.auth.NotLoggedInException
import software.medusa.counter.cli.auth.OAuth2TokenClient
import software.medusa.counter.cli.config.ConfigStore
import software.medusa.counter.cli.config.Environment

/**
 * Base for commands that talk to the counter API. Builds the authenticated CounterApiClient for the
 * resolved environment — resolving the OAuth secret and loading the cached sign-in (else failing
 * with a 'run login' hint) — and runs [run] against it, closing the client afterward.
 */
abstract class ManagementCommand(name: String) : AppCommand(name = name) {
  final override fun run(environment: Environment, configStore: ConfigStore) {
    val tokenClient =
        OAuth2TokenClient(
            GoogleOAuth.TOKEN_ENDPOINT,
            environment.oauthClientId,
            environment.oauthClientSecret,
        )
    val idTokenProvider =
        ConfigIdTokenProvider.load(Clock.System, configStore, DefaultTokenRefresher(tokenClient))
            ?: throw NotLoggedInException("Not signed in. Run 'ms-counter login' first.")

    CounterApiClient(environment.apiEndpoint, idTokenProvider).use { run(it) }
  }

  abstract fun run(apiClient: CounterApiClient)
}
