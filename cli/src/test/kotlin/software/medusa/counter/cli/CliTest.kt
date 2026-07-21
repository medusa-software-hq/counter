package software.medusa.counter.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

class BuildConfigTest {
  @Test
  fun `resolve prefers env, then baked, then default`() {
    assertEquals("env", BuildConfig.resolve("env", "baked", "default"))
    assertEquals("baked", BuildConfig.resolve(null, "baked", "default"))
    assertEquals("default", BuildConfig.resolve(null, null, "default"))
  }

  @Test
  fun `resolve treats blank as absent at every level`() {
    assertEquals("baked", BuildConfig.resolve("  ", "baked", "default"))
    assertEquals("default", BuildConfig.resolve("", "", "default"))
    assertNull(BuildConfig.resolve(null, null, null))
  }
}

class OAuthTest {
  @Test
  fun `PKCE S256 challenge matches the RFC 7636 test vector`() {
    // RFC 7636 Appendix B.
    val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
    assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", codeChallenge(verifier))
  }

  @Test
  fun `buildAuthUrl carries the PKCE + loopback parameters`() {
    val url = buildAuthUrl("cid", "http://127.0.0.1:1234", "chal", "st")
    assertTrue("client_id=cid" in url)
    assertTrue("code_challenge=chal" in url)
    assertTrue("code_challenge_method=S256" in url)
    assertTrue("state=st" in url)
    assertTrue("access_type=offline" in url)
  }

  @Test
  fun `parseQuery decodes callback params`() {
    val q = parseQuery("code=abc&state=xyz&error_description=some%20thing")
    assertEquals("abc", q["code"])
    assertEquals("xyz", q["state"])
    assertEquals("some thing", q["error_description"])
  }

  @Test
  fun `generateCodeVerifier is high-entropy and url-safe`() {
    val v = generateCodeVerifier()
    assertTrue(v.length >= 40)
    assertTrue(v.all { it.isLetterOrDigit() || it == '-' || it == '_' })
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
