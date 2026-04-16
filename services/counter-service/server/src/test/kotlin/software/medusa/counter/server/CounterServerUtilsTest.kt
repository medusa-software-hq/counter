package software.medusa.counter.server

import kotlin.test.Test
import kotlin.test.assertContains

class CounterServerUtilsTest {
  @Test
  fun testSayHello() {
    assertContains(
        CounterServerUtils.sayHello(),
        "Hello",
    )
  }
}
