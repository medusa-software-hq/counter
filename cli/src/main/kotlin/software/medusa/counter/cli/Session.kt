package software.medusa.counter.cli

import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * Raised when there's no usable session — the caller turns it into a "run ms-counter login" hint.
 */
class NotLoggedInException(message: String) : Exception(message)

/**
 * Supplies a currently-valid Google ID token for API calls: hands back the cached one while it's
 * still good, and silently refreshes it (no browser) when it's expired. Only a revoked/expired
 * refresh token forces a fresh `ms-counter login`. Environment-agnostic by construction — the
 * caller passes the environment's [configStore] and its [refresher] (see [DefaultTokenRefresher]).
 */
class Session(
    private val configStore: ConfigStore,
    private val refresher: TokenRefresher,
    private val clock: Clock = Clock.System,
) : IdTokenProvider {
  override fun idToken(): String {
    val credentials =
        configStore.loadCredentials()
            ?: throw NotLoggedInException("Not signed in. Run 'ms-counter login' first.")

    // Refresh a little early so a token doesn't expire mid-request.
    if (credentials.idTokenExpiresAt > clock.now() + REFRESH_SKEW) {
      return credentials.idToken
    }

    val refreshed =
        try {
          refresher.refresh(credentials.refreshToken)
        } catch (e: OAuthException) {
          throw NotLoggedInException("Session expired (${e.code}). Run 'ms-counter login' again.")
        }

    configStore.saveCredentials(
        credentials.copy(idToken = refreshed.idToken, idTokenExpiresAt = refreshed.expiresAt)
    )
    return refreshed.idToken
  }

  companion object {
    // Refresh this far ahead of expiry so a token can't lapse mid-request.
    private val REFRESH_SKEW = 30.seconds
  }
}
