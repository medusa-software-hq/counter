package software.medusa.counter.server

import software.medusa.counter.v1.CounterServiceGrpcKt
import software.medusa.counter.v1.SayHelloRequest
import software.medusa.counter.v1.SayHelloResponse

class CounterServiceImpl : CounterServiceGrpcKt.CounterServiceCoroutineImplBase() {
  override suspend fun sayHello(request: SayHelloRequest): SayHelloResponse =
      SayHelloResponse.newBuilder().setMessage("Hello from counter-service!").build()
}
