package software.medusa.counter.server

import com.linecorp.armeria.server.Server
import com.linecorp.armeria.server.healthcheck.HealthCheckService

private const val portEnvVarName = "PORT"
private const val clientIdEnvVarName = "GOOGLE_CLIENT_ID"
private const val allowedDomainEnvVarName = "GOOGLE_ALLOWED_DOMAIN"

fun main() {
  val port =
      System.getenv(portEnvVarName)?.toIntOrNull()
          ?: error("$portEnvVarName environment variable must be set to a valid integer")

  val clientId =
      System.getenv(clientIdEnvVarName)
          ?: error("$clientIdEnvVarName environment variable must be set")

  val allowedDomain =
      System.getenv(allowedDomainEnvVarName)
          ?: error("$allowedDomainEnvVarName environment variable must be set")

  val auth = GoogleIdTokenAuthDecorator(clientId, allowedDomain)

  val server =
      Server.builder()
          .apply {
            http(port)

            // Health check is unauthenticated (used by Cloud Run probes).
            service("/health", HealthCheckService.of())

            // All other routes require a valid Google ID token.
            service("/", CounterService.decorate(auth))
          }
          .build()

  server.start().join()
}
