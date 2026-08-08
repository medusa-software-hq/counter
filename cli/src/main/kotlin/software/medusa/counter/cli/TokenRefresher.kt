package software.medusa.counter.cli

/** Mints a fresh [TokenSet] from a stored refresh token (no browser). */
interface TokenRefresher {
  fun refresh(refreshToken: String): TokenSet
}
