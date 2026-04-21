package software.medusa.counter.server

import software.medusa.counter.v1.CounterServiceGrpcKt
import software.medusa.counter.v1.DecrementRequest
import software.medusa.counter.v1.DecrementResponse
import software.medusa.counter.v1.GetCountRequest
import software.medusa.counter.v1.GetCountResponse
import software.medusa.counter.v1.IncrementRequest
import software.medusa.counter.v1.IncrementResponse

class CounterServiceImpl : CounterServiceGrpcKt.CounterServiceCoroutineImplBase() {
  private var counter = 0

  override suspend fun getCount(request: GetCountRequest): GetCountResponse =
      GetCountResponse.newBuilder().setCount(counter).build()

  override suspend fun increment(request: IncrementRequest): IncrementResponse =
      IncrementResponse.newBuilder().setCount(++counter).build()

  override suspend fun decrement(request: DecrementRequest): DecrementResponse =
      DecrementResponse.newBuilder().setCount(--counter).build()
}
