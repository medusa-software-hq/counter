package software.medusa.counter.server

private const val portEnvVarName = "PORT"
private const val webClientIdEnvVarName = "GOOGLE_WEB_CLIENT_ID"
private const val cliClientIdEnvVarName = "GOOGLE_CLI_CLIENT_ID"
private const val allowedDomainEnvVarName = "GOOGLE_ALLOWED_DOMAIN"
private const val corsOriginRegexEnvVarName = "CORS_ALLOWED_ORIGIN_REGEX"
private const val databaseUrlEnvVarName = "DATABASE_URL"

// IAP dual-mode config. Both are dormant until Google IAP is enabled in front of the API, so both
// tolerate being unset/empty: today's deploy sets neither and the IAP branch stays inert.
//   IAP_API_AUDIENCE     — the API service's IAP audience string; empty ⇒ IAP assertions rejected.
//   TRUSTED_PROXY_EMAILS — comma-separated SA emails allowed through IAP without an `hd` claim.
private const val iapApiAudienceEnvVarName = "IAP_API_AUDIENCE"
private const val trustedProxyEmailsEnvVarName = "TRUSTED_PROXY_EMAILS"

fun main() {
  val port =
      System.getenv(portEnvVarName)?.toIntOrNull()
          ?: error("$portEnvVarName environment variable must be set to a valid integer")

  val webClientId =
      System.getenv(webClientIdEnvVarName)
          ?: error("$webClientIdEnvVarName environment variable must be set")

  // Required: the CLI (Desktop) OAuth client. The API also accepts ID tokens whose audience is the
  // CLI client, so `ms-counter` can call it. This is a distinct OAuth client from the web SPA.
  val cliClientId =
      System.getenv(cliClientIdEnvVarName)
          ?: error("$cliClientIdEnvVarName environment variable must be set")

  val allowedDomain =
      System.getenv(allowedDomainEnvVarName)
          ?: error("$allowedDomainEnvVarName environment variable must be set")

  val corsOriginRegex =
      System.getenv(corsOriginRegexEnvVarName)
          ?: error("$corsOriginRegexEnvVarName environment variable must be set")

  val databaseUrl =
      System.getenv(databaseUrlEnvVarName)
          ?: error("$databaseUrlEnvVarName environment variable must be set")

  // Tolerant of unset/empty: an empty audience yields a null verifier (IAP assertions rejected),
  // and an empty/unset list yields an empty allowlist. The IAP branch is dormant until the flip.
  val iapApiAudience = System.getenv(iapApiAudienceEnvVarName).orEmpty()
  val iapVerifier =
      iapApiAudience.takeIf { it.isNotEmpty() }?.let { IapAssertionVerifier.build(it) }

  val trustedProxyEmails =
      System.getenv(trustedProxyEmailsEnvVarName)
          .orEmpty()
          .split(',')
          .map { it.trim() }
          .filter { it.isNotEmpty() }
          .toSet()

  buildServer(
          originRegex = corsOriginRegex,
          port = port,
          auth =
              GoogleIdTokenAuthDecorator(
                  allowedClientIds = setOf(webClientId, cliClientId),
                  allowedDomain = allowedDomain,
                  iapVerifier = iapVerifier,
                  trustedProxyEmails = trustedProxyEmails,
              ),
          counterStore = PostgresCounterStore.build(databaseUrl),
      )
      .start()
      .join()
}
