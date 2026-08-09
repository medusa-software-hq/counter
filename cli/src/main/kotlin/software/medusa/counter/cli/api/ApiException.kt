package software.medusa.counter.cli.api

/** A CounterService call that failed in a way worth showing the user a clean message for. */
class ApiException(message: String) : Exception(message)
