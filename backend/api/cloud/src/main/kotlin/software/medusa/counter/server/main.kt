package software.medusa.counter.server

private const val portEnvVarName = "PORT"
private const val corsOriginRegexEnvVarName = "CORS_ALLOWED_ORIGIN_REGEX"
private const val databaseUrlEnvVarName = "DATABASE_URL"

fun main() {
  val port =
      System.getenv(portEnvVarName)?.toIntOrNull()
          ?: error("$portEnvVarName environment variable must be set to a valid integer")

  val corsOriginRegex =
      System.getenv(corsOriginRegexEnvVarName)
          ?: error("$corsOriginRegexEnvVarName environment variable must be set")

  val databaseUrl =
      System.getenv(databaseUrlEnvVarName)
          ?: error("$databaseUrlEnvVarName environment variable must be set")

  // No real authentication yet: the identity provider is mid-migration, so this runs a pass-through
  // gate. The service is kept private upstream until a JWT verifier is wired in.
  buildServer(
          originRegex = corsOriginRegex,
          port = port,
          auth = NoOpAuthDecorator,
          counterStore = PostgresCounterStore.build(databaseUrl),
      )
      .start()
      .join()
}
