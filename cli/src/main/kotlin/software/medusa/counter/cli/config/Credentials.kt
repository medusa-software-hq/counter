package software.medusa.counter.cli.config

import kotlinx.serialization.Serializable

/**
 * Cached sign-in state, persisted (file 0600, dir 0700) via [ConfigStore]. For now just an opaque
 * dev bearer [token] stored by `login`; a later issue replaces this with a real Cognito session.
 */
@Serializable data class Credentials(val token: String)
