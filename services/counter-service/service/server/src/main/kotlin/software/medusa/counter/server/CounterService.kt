package software.medusa.counter.server

import com.linecorp.armeria.common.HttpRequest
import com.linecorp.armeria.common.HttpResponse
import com.linecorp.armeria.common.MediaType
import com.linecorp.armeria.server.HttpService
import com.linecorp.armeria.server.ServiceRequestContext

data object CounterService : HttpService {
  override fun serve(
      ctx: ServiceRequestContext,
      req: HttpRequest,
  ): HttpResponse = HttpResponse.of(MediaType.PLAIN_TEXT, "Hello from counter-service!")
}
