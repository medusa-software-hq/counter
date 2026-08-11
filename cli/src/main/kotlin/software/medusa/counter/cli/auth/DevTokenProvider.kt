package software.medusa.counter.cli.auth

import software.medusa.counter.cli.config.ConfigStore

/**
 * A development stand-in for real identity: hands back an optional bearer token from the
 * [DEV_TOKEN_ENV] env var, else the one cached by `login` in [ConfigStore], else null. The backend
 * runs no-op auth for now, so a null token is fine — a later issue replaces this with Cognito.
 */
class DevTokenProvider(private val configStore: ConfigStore) : TokenProvider {
  override fun provideToken(): String? =
      System.getenv(DEV_TOKEN_ENV)?.ifBlank { null }
          ?: configStore.loadCredentials()?.token?.ifBlank { null }

  companion object {
    const val DEV_TOKEN_ENV = "COUNTER_DEV_TOKEN"
  }
}
