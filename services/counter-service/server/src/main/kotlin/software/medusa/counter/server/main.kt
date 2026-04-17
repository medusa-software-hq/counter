package software.medusa.counter.server

import com.linecorp.armeria.common.HttpMethod
import com.linecorp.armeria.server.Server
import com.linecorp.armeria.server.cors.CorsService
import com.linecorp.armeria.server.grpc.GrpcService
import com.linecorp.armeria.server.healthcheck.HealthCheckService

private const val portEnvVarName = "PORT"
private const val allowedOriginEnvVarName = "CORS_ALLOWED_ORIGIN"

fun main() {
  val port =
      System.getenv(portEnvVarName)?.toIntOrNull()
          ?: error("$portEnvVarName environment variable must be set to a valid integer")

  val allowedOrigin =
      System.getenv(allowedOriginEnvVarName)
          ?: error("$allowedOriginEnvVarName environment variable must be set")

  val cors =
      CorsService.builderForOriginRegex(allowedOrigin)
          .apply {
            allowRequestMethods(
                HttpMethod.POST,
                HttpMethod.OPTIONS,
            )

            allowRequestHeaders(
                "authorization",
                "content-type",
                "x-grpc-web",
                "x-user-agent",
                "grpc-timeout",
                "connect-protocol-version",
                "connect-timeout-ms",
            )

            exposeHeaders(
                "grpc-status",
                "grpc-message",
                "content-type",
            )
          }
          .newDecorator()

  val grpcService =
      GrpcService.builder()
          .apply {
            // Primary gRPC service
            addService(CounterServiceImpl())

            // Required for gRPC-Web support
            enableUnframedRequests(true)
          }
          .build()

  val server =
      Server.builder()
          .apply {
            http(port)

            serviceUnder("/", grpcService.decorate(cors))

            service("/health", HealthCheckService.of())
          }
          .build()

  server.start().join()
}
