package software.medusa.counter.cli.auth

/**
 * The default [TokenRefresher]: delegates to an [OAuthTokenClient] configured for the environment's
 * OAuth client. Building that client — resolving the client secret — is the composition root's job,
 * so this refresher no longer needs to know about the environment at all.
 */
class DefaultTokenRefresher(private val oAuthTokenClient: OAuthTokenClient) : TokenRefresher {
  override fun refresh(refreshToken: String): TokenSet = oAuthTokenClient.refresh(refreshToken)
}
