package software.medusa.counter.cli

import kotlinx.serialization.Serializable

/**
 * Cached sign-in: the long-lived refresh token plus the most recent ID token and its expiry. Loaded
 * and persisted (file 0600, dir 0700) via [ConfigStore]. The refresh token is the sensitive field —
 * it stands in for the human until revoked.
 */
@Serializable
data class Credentials(
    val refreshToken: String,
    val idToken: String,
    val idTokenExpiresAtEpochSec: Long,
    val email: String,
)
