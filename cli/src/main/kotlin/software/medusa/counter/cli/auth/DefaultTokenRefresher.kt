package software.medusa.counter.cli.auth

/**
 * The default [TokenRefresher]: delegates to an [OAuth2TokenClient] configured for the
 * environment's OAuth client. Building that client — resolving the client secret — is the
 * composition root's job, so this refresher no longer needs to know about the environment at all.
 */
class DefaultTokenRefresher(private val oAuth2TokenClient: OAuth2TokenClient) : TokenRefresher {
  override fun refresh(refreshToken: String): TokenSet = oAuth2TokenClient.refresh(refreshToken)
}
