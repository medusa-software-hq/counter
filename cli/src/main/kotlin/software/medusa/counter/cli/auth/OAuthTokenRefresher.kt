package software.medusa.counter.cli.auth

/** Mints a fresh [TokenSet] from a stored refresh token (no browser). */
interface OAuthTokenRefresher {
  fun refresh(refreshToken: String): TokenSet
}
