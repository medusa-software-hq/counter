# IAP migration infra. Provisioned ahead of the flip; nothing here gates traffic until IAP is
# enabled on the Cloud Run services (a deploy-time change).

# Runtime service account for the web (SPA) Cloud Run service. After the flip the web service's
# Envoy sidecar runs as this SA and mints an ID token (audience = the API's IAP) to satisfy the
# API's IAP on the browser's behalf — the "trusted proxy" hop the API's auth gate recognizes.
# Inert on its own: a bare SA with no bindings grants nothing.
resource "google_service_account" "spa_web" {
  project      = local.gcp_project_id
  account_id   = "spa-web"
  display_name = "SPA web runtime (IAP proxy hop)"

  depends_on = [google_project_service.apis["iam.googleapis.com"]]
}

output "spa_web_sa_email" {
  description = "Email of the SPA web runtime service account (the API's trusted-proxy principal)."
  value       = google_service_account.spa_web.email
}
