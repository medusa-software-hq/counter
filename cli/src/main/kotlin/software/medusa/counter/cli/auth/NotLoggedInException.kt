package software.medusa.counter.cli.auth

/**
 * Raised when there's no usable session — the caller turns it into a "run ms-counter login" hint.
 */
class NotLoggedInException(message: String) : Exception(message)
