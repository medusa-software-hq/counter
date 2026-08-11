package software.medusa.counter.cli.api

import io.grpc.Grpc
import io.grpc.InsecureChannelCredentials
import io.grpc.ManagedChannel
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.TlsChannelCredentials
import java.util.concurrent.TimeUnit
import software.medusa.counter.cli.auth.TokenProvider
import software.medusa.counter.v1.CounterServiceGrpc
import software.medusa.counter.v1.CounterServiceGrpc.CounterServiceBlockingStub
import software.medusa.counter.v1.DecrementRequest
import software.medusa.counter.v1.GetCountRequest
import software.medusa.counter.v1.IncrementRequest

/**
 * Talks to CounterService over gRPC. A [BearerTokenInterceptor] — attached to the stub once — puts
 * the caller's bearer token (when one is configured) on every call, fetched via [tokenProvider] as
 * the call starts. [close] shuts the channel down — the CLI uses one client per command.
 */
class CounterApiClient(endpoint: ApiEndpoint, tokenProvider: TokenProvider) : AutoCloseable {
  private val channel: ManagedChannel = channelFor(endpoint)
  private val stub: CounterServiceBlockingStub =
      CounterServiceGrpc.newBlockingStub(channel)
          .withInterceptors(BearerTokenInterceptor(tokenProvider))

  fun getCount(): Int = call { stub.getCount(GetCountRequest.getDefaultInstance()).count }

  fun increment(): Int = call { stub.increment(IncrementRequest.getDefaultInstance()).count }

  fun decrement(): Int = call { stub.decrement(DecrementRequest.getDefaultInstance()).count }

  private inline fun <T> call(block: () -> T): T =
      try {
        block()
      } catch (e: StatusRuntimeException) {
        throw asApiException(e)
      }

  override fun close() {
    channel.shutdownNow()
    channel.awaitTermination(SHUTDOWN_TIMEOUT_SEC, TimeUnit.SECONDS)
  }

  companion object {
    private const val SHUTDOWN_TIMEOUT_SEC = 5L

    private fun channelFor(endpoint: ApiEndpoint): ManagedChannel {
      val credentials =
          if (endpoint.useTls) TlsChannelCredentials.create()
          else InsecureChannelCredentials.create()
      return Grpc.newChannelBuilderForAddress(endpoint.host, endpoint.port, credentials).build()
    }

    private fun asApiException(e: StatusRuntimeException): ApiException =
        when (e.status.code) {
          Status.Code.UNAUTHENTICATED,
          Status.Code.PERMISSION_DENIED ->
              ApiException(
                  "The API rejected your identity (${e.status.code}). Your session may have lapsed, " +
                      "or your account isn't allowed — try 'ms-counter login' again."
              )
          else -> ApiException("API error (${e.status.code}): ${e.status.description ?: e.message}")
        }
  }
}
