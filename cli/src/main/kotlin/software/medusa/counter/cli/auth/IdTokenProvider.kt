package software.medusa.counter.cli.auth

/**
 * Supplies a currently-valid Google ID token to present as the bearer credential on an API call.
 */
interface IdTokenProvider {
  fun idToken(): String
}
