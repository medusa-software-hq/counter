package software.medusa.counter.cli

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class EnvironmentTest {
  @Test
  fun `absent or prod selects Prod`() {
    assertEquals(Environment.Prod, Environment.current(raw = null))
    assertEquals(Environment.Prod, Environment.current(raw = ""))
    assertEquals(Environment.Prod, Environment.current(raw = "prod"))
    assertEquals(Environment.Prod, Environment.current(raw = "PRODUCTION"))
  }

  @Test
  fun `staging is case-insensitive`() {
    assertEquals(Environment.Staging, Environment.current(raw = "staging"))
    assertEquals(Environment.Staging, Environment.current(raw = " Staging "))
  }

  @Test
  fun `unknown environment is rejected`() {
    assertFailsWith<EnvironmentSelectionException> { Environment.current(raw = "prd") }
  }

  @Test
  fun `local requires config path and a valid port`() {
    val env = Environment.current(raw = "local", localConfigPath = "/tmp/x", localPort = "8081")
    assertTrue(env is Environment.Local)
    assertEquals(ApiEndpoint("127.0.0.1", 8081, useTls = false), env.apiEndpoint)
    assertFailsWith<EnvironmentSelectionException> {
      Environment.current(raw = "local", localConfigPath = null, localPort = "8081")
    }
    assertFailsWith<EnvironmentSelectionException> {
      Environment.current(raw = "local", localConfigPath = "/tmp/x", localPort = null)
    }
    assertFailsWith<EnvironmentSelectionException> {
      Environment.current(raw = "local", localConfigPath = "/tmp/x", localPort = "nope")
    }
  }

  @Test
  fun `prod and staging are fully partitioned`() {
    val base = Path.of("/base")
    assertEquals(Path.of("/base/prod"), Environment.Prod.resolveConfigDirPath(base))
    assertEquals(Path.of("/base/staging"), Environment.Staging.resolveConfigDirPath(base))
    assertNotEquals(
        Environment.Prod.resolveConfigDirPath(base),
        Environment.Staging.resolveConfigDirPath(base),
    )
    // Separate OAuth clients per environment — the credential boundary is the environment boundary.
    assertNotEquals(Environment.Prod.oauthClientId, Environment.Staging.oauthClientId)
    assertEquals(null, Environment.Prod.marker)
    assertEquals("[staging]", Environment.Staging.marker)
  }
}
