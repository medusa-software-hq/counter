package software.medusa.counter.cli.api

import io.grpc.CallOptions
import io.grpc.Channel
import io.grpc.ClientCall
import io.grpc.ClientInterceptor
import io.grpc.ForwardingClientCall.SimpleForwardingClientCall
import io.grpc.Metadata
import io.grpc.MethodDescriptor
import software.medusa.counter.cli.auth.TokenProvider

/**
 * Attaches the caller's bearer token as an `Authorization: Bearer` header on every gRPC call.
 * Attach it to the stub once; gRPC runs it per call, fetching the token from [tokenProvider] as
 * each call starts. When the provider returns null (no token configured — fine while the backend
 * runs no-op auth) the header is omitted.
 */
class BearerTokenInterceptor(private val tokenProvider: TokenProvider) : ClientInterceptor {
  override fun <ReqT, RespT> interceptCall(
      method: MethodDescriptor<ReqT, RespT>,
      callOptions: CallOptions,
      next: Channel,
  ): ClientCall<ReqT, RespT> =
      object : SimpleForwardingClientCall<ReqT, RespT>(next.newCall(method, callOptions)) {
        override fun start(responseListener: ClientCall.Listener<RespT>, headers: Metadata) {
          tokenProvider.provideToken()?.let { headers.put(AUTHORIZATION, "Bearer $it") }
          super.start(responseListener, headers)
        }
      }

  companion object {
    private val AUTHORIZATION: Metadata.Key<String> =
        Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER)
  }
}
