package software.medusa.counter.cli

/**
 * OAuth client + API endpoint the CLI uses. The API base URL lives in [BuildConfig]. The client id
 * is public and lives in source; the client secret — which, for a Desktop OAuth client, Google
 * explicitly does not treat as confidential — is baked into the fat jar at build time from a
 * generated resource (the Publish CLI workflow passes it as `-PcliOauthClientSecret` from an
 * Actions secret, so it isn't committed). A local build has none baked in and falls back to the env
 * var, so dev builds still work.
 */
object CounterConfig {
  /**
   * The prod counter Desktop OAuth client (project ms-counter-1175e509). Public; safe in source.
   * Kept in sync with infra/common's prod `cli_client_id` (the API's accepted CLI audience). The
   * matching non-confidential Desktop secret is baked at publish from the [CLIENT_SECRET_ENV]
   * Actions secret.
   */
  const val CLIENT_ID = "390879863874-2lni09664lo24g44kakjceu2j7s164nr.apps.googleusercontent.com"

  const val CLIENT_SECRET_ENV = "COUNTER_CLI_OAUTH_CLIENT_SECRET"

  /**
   * The OAuth client secret, or null if this build has none baked in and no env override is set —
   * in which case `ms-counter login` can't run and the caller reports how to fix it.
   */
  val clientSecret: String?
    get() =
        BuildConfig.resolve(
            System.getenv(CLIENT_SECRET_ENV),
            BuildConfig.bakedProperty("oauthClientSecret"),
            null,
        )

  /** The counter API base URL (see [BuildConfig.apiBaseUrl]). */
  val apiBaseUrl: String
    get() = BuildConfig.apiBaseUrl
}
