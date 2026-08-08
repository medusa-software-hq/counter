package software.medusa.counter.cli.auth

import kotlin.time.Instant

/** A Google ID token plus the refresh token and the moment the ID token expires. */
data class TokenSet(val idToken: String, val refreshToken: String?, val expiresAt: Instant)
