package software.medusa.counter.server

import com.linecorp.armeria.common.HttpMethod
import com.linecorp.armeria.server.Server
import com.linecorp.armeria.server.cors.CorsService
import com.linecorp.armeria.server.healthcheck.HealthCheckService

private const val localPort = 8081
private const val localCorsOriginRegex = """http://localhost(:\d+)?"""

fun main() {
  val cors =
      CorsService.builderForOriginRegex(localCorsOriginRegex)
          .apply {
            allowRequestMethods(HttpMethod.GET, HttpMethod.OPTIONS)
            allowRequestHeaders("authorization", "content-type")
          }
          .newDecorator()

  val server =
      Server.builder()
          .apply {
            http(localPort)

            service("/health", HealthCheckService.of())

            // No auth in local dev.
            service("/", CounterService.decorate(NoOpAuthDecorator).decorate(cors))
          }
          .build()

  server.start().join()
}
