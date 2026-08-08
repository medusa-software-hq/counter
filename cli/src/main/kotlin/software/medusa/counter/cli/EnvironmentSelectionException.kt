package software.medusa.counter.cli

/** Raised when `COUNTER_ENVIRONMENT` (or a `local`-only variable) is set to something unusable. */
class EnvironmentSelectionException(message: String) : Exception(message)
