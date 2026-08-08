package software.medusa.counter.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

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
    assertEquals("http://127.0.0.1:8081", env.apiBaseUrl)
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
    assertTrue(Environment.Prod.configDir.endsWith("prod"))
    assertTrue(Environment.Staging.configDir.endsWith("staging"))
    assertNotEquals(Environment.Prod.configDir, Environment.Staging.configDir)
    // Separate OAuth clients per environment — the credential boundary is the environment boundary.
    assertNotEquals(Environment.Prod.oauthClientId, Environment.Staging.oauthClientId)
    assertEquals(null, Environment.Prod.marker)
    assertEquals("[staging]", Environment.Staging.marker)
  }
}

class ApiClientTest {
  @Test
  fun `CountResponse parses proto3 JSON`() {
    val json = Json { ignoreUnknownKeys = true }
    assertEquals(5, json.decodeFromString<CountResponse>("""{"count":5}""").count)
    // A zero counter serializes with the field omitted in proto3 JSON.
    assertEquals(0, json.decodeFromString<CountResponse>("{}").count)
  }
}
