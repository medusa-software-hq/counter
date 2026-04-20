package software.medusa.counter.server

import com.linecorp.armeria.common.HttpMethod
import com.linecorp.armeria.server.Server
import com.linecorp.armeria.server.cors.CorsService
import com.linecorp.armeria.server.healthcheck.HealthCheckService

private const val portEnvVarName = "PORT"
private const val clientIdEnvVarName = "GOOGLE_CLIENT_ID"
private const val allowedDomainEnvVarName = "GOOGLE_ALLOWED_DOMAIN"
private const val corsOriginRegexEnvVarName = "CORS_ALLOWED_ORIGIN_REGEX"

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

  val corsOriginRegex =
      System.getenv(corsOriginRegexEnvVarName)
          ?: error("$corsOriginRegexEnvVarName environment variable must be set")

  val auth = GoogleIdTokenAuthDecorator(clientId, allowedDomain)

  val cors =
      CorsService.builderForOriginRegex(corsOriginRegex)
          .apply {
            allowRequestMethods(HttpMethod.GET, HttpMethod.OPTIONS)
            allowRequestHeaders("authorization", "content-type")
          }
          .newDecorator()

  val server =
      Server.builder()
          .apply {
            http(port)

            // Health check is unauthenticated (used by Cloud Run probes).
            service("/health", HealthCheckService.of())

            // All other routes require a valid Google ID token.
            service("/", CounterService.decorate(auth).decorate(cors))
          }
          .build()

  server.start().join()
}
