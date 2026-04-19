package software.medusa.counter.server

import com.linecorp.armeria.common.HttpResponse
import com.linecorp.armeria.common.MediaType
import com.linecorp.armeria.server.Server
import com.linecorp.armeria.server.healthcheck.HealthCheckService

private const val portEnvVarName = "PORT"

fun main() {
  val port =
      System.getenv(portEnvVarName)?.toIntOrNull()
          ?: error("$portEnvVarName environment variable must be set to a valid integer")

  val server =
      Server.builder()
          .apply {
            http(port)

            service("/") { _, _ ->
              HttpResponse.of(MediaType.PLAIN_TEXT, "Hello from counter-service!")
            }

            service("/health", HealthCheckService.of())
          }
          .build()

  server.start().join()
}
