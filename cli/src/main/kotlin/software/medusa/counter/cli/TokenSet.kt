package software.medusa.counter.cli

/** A Google ID token plus the refresh token and the ID token's expiry (epoch seconds). */
data class TokenSet(val idToken: String, val refreshToken: String?, val expiresAtEpochSec: Long)
