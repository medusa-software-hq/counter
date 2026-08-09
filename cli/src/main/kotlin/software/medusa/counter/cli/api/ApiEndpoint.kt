package software.medusa.counter.cli.api

/** Where a CounterService lives: host + port, and whether to dial it over TLS. */
data class ApiEndpoint(val host: String, val port: Int, val useTls: Boolean)
