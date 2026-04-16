package software.medusa.counter.server

import software.medusa.counter.v1.CounterServiceGrpcKt
import software.medusa.counter.v1.GetCounterRequest
import software.medusa.counter.v1.GetCounterResponse
import software.medusa.counter.v1.IncrementCounterRequest
import software.medusa.counter.v1.IncrementCounterResponse

class CounterServiceImpl : CounterServiceGrpcKt.CounterServiceCoroutineImplBase() {
  private var counter = 0

  override suspend fun getCounter(
      request: GetCounterRequest,
  ): GetCounterResponse =
      GetCounterResponse.newBuilder()
          .apply {
            // Return the current value of the counter
            count = counter
          }
          .build()

  override suspend fun incrementCounter(
      request: IncrementCounterRequest,
  ): IncrementCounterResponse =
      IncrementCounterResponse.newBuilder()
          .apply {
            // Increment the counter and return the new value
            count = ++counter
          }
          .build()
}
