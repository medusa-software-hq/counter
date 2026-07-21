package software.medusa.counter.cli

import java.util.Properties

/**
 * Values baked into the published fat jar at build time. The Publish CLI workflow bakes them
 * (`-PcliApiBaseUrl` from the `API_URL` Actions variable — the same URL the SPA builds against —
 * and `-PcliOauthClientSecret` from an Actions secret). A local build has nothing baked and falls
 * back to the prod URL, overridable via [API_URL_ENV] for local development.
 */
object BuildConfig {
  /** Override the API endpoint for local dev, e.g. `COUNTER_API_URL=http://localhost:8081`. */
  const val API_URL_ENV = "COUNTER_API_URL"

  // The prod backend URL — the fallback when nothing is baked (a local build). The stable
  // front-door host (api.<web host>), whose single source of truth is infra/common's api_host_name,
  // which the API_URL Actions variable is also computed from.
  const val DEFAULT_API_BASE_URL = "https://api.counter-baseline.medusa.software"

  private val baked: Properties =
      Properties().apply {
        BuildConfig::class.java.getResourceAsStream("/counter-cli-build.properties")?.use {
          load(it)
        }
      }

  /** A baked property, or null if absent/blank (a local build with nothing baked in). */
  fun bakedProperty(key: String): String? = baked.getProperty(key)?.ifBlank { null }

  /** The backend API base URL: env override (dev) > value baked at build > prod default. */
  val apiBaseUrl: String
    get() = resolve(System.getenv(API_URL_ENV), bakedProperty("apiBaseUrl"), DEFAULT_API_BASE_URL)!!

  /** env override > value baked at build > default. Blank is treated as absent at every level. */
  internal fun resolve(envValue: String?, bakedValue: String?, default: String?): String? =
      envValue?.ifBlank { null } ?: bakedValue?.ifBlank { null } ?: default
}
