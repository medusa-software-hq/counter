package software.medusa.counter.cli.api

import io.grpc.Grpc
import io.grpc.InsecureChannelCredentials
import io.grpc.ManagedChannel
import io.grpc.Metadata
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.TlsChannelCredentials
import io.grpc.stub.MetadataUtils
import java.util.concurrent.TimeUnit
import software.medusa.counter.cli.auth.IdTokenProvider
import software.medusa.counter.v1.CounterServiceGrpc
import software.medusa.counter.v1.CounterServiceGrpc.CounterServiceBlockingStub
import software.medusa.counter.v1.DecrementRequest
import software.medusa.counter.v1.GetCountRequest
import software.medusa.counter.v1.IncrementRequest

/**
 * Talks to CounterService over gRPC, presenting the caller's Google ID token as a bearer credential
 * on every call. The token is fetched (and silently refreshed) via [idTokenProvider] just before
 * each request, so a lapsed session surfaces as [NotLoggedInException] rather than a gRPC error.
 * [close] shuts the channel down — the CLI uses one client per command.
 */
class CounterApiClient(endpoint: ApiEndpoint, private val idTokenProvider: IdTokenProvider) :
    AutoCloseable {
  private val channel: ManagedChannel = channelFor(endpoint)
  private val stub: CounterServiceBlockingStub = CounterServiceGrpc.newBlockingStub(channel)

  fun getCount(): Int = call { authed().getCount(GetCountRequest.getDefaultInstance()).count }

  fun increment(): Int = call { authed().increment(IncrementRequest.getDefaultInstance()).count }

  fun decrement(): Int = call { authed().decrement(DecrementRequest.getDefaultInstance()).count }

  /**
   * The stub with a fresh bearer token attached; fetching the token may throw
   * [NotLoggedInException].
   */
  private fun authed(): CounterServiceBlockingStub {
    val headers = Metadata().apply { put(AUTHORIZATION, "Bearer ${idTokenProvider.idToken()}") }
    return stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers))
  }

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

    private val AUTHORIZATION: Metadata.Key<String> =
        Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER)

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
