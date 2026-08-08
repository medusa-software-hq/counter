package software.medusa.counter.cli.auth

import software.medusa.counter.cli.config.Environment

/**
 * The [TokenRefresher] for an [Environment]: mints a fresh ID token via that environment's OAuth
 * client (its id plus the secret this build resolves for it). Fails with [NotLoggedInException]
 * when no client secret is available, so `login` can point the user at the fix.
 */
class DefaultTokenRefresher(private val env: Environment) : TokenRefresher {
  override fun refresh(refreshToken: String): TokenSet {
    val secret =
        env.oauthClientSecret
            ?: throw NotLoggedInException(
                "This CLI build has no OAuth client secret for ${env.label}; set " +
                    "${env.oauthClientSecretEnvVar}."
            )
    return OAuth2TokenClient(GoogleOAuth.TOKEN_ENDPOINT, env.oauthClientId, secret)
        .refresh(refreshToken)
  }
}
