package software.medusa.counter.cli

import java.nio.file.Path

/**
 * Raised when there's no usable session — the caller turns it into a "run ms-counter login" hint.
 */
class NotLoggedInException(message: String) : Exception(message)

/**
 * Default token refresher: mints a fresh ID token via the OAuth client secret this build resolves.
 */
private fun defaultRefresher(): (String) -> TokenSet = { refreshToken ->
  val secret =
      CounterConfig.clientSecret
          ?: throw NotLoggedInException(
              "This CLI build has no OAuth client secret; set ${CounterConfig.CLIENT_SECRET_ENV}."
          )
  CounterOAuth(clientSecret = secret).refresh(refreshToken)
}

/**
 * Supplies a currently-valid Google ID token for API calls: hands back the cached one while it's
 * still good, and silently refreshes it (no browser) when it's expired. Only a revoked/expired
 * refresh token forces a fresh `ms-counter login`.
 */
class Session(
    private val dir: Path = configDir(),
    private val nowEpochSec: () -> Long = { System.currentTimeMillis() / 1000 },
    private val refresher: (String) -> TokenSet = defaultRefresher(),
) {
  fun currentIdToken(): String {
    val credentials =
        loadCredentials(dir)
            ?: throw NotLoggedInException("Not signed in. Run 'ms-counter login' first.")

    // Refresh a little early so a token doesn't expire mid-request.
    if (credentials.idTokenExpiresAtEpochSec > nowEpochSec() + 30) {
      return credentials.idToken
    }

    val refreshed =
        try {
          refresher(credentials.refreshToken)
        } catch (e: OAuthException) {
          throw NotLoggedInException("Session expired (${e.code}). Run 'ms-counter login' again.")
        }

    saveCredentials(
        credentials.copy(
            idToken = refreshed.idToken,
            idTokenExpiresAtEpochSec = refreshed.expiresAtEpochSec,
        ),
        dir,
    )
    return refreshed.idToken
  }
}
