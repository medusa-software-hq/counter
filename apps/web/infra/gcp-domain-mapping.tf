# Map the custom subdomain directly to the Cloud Run service (no load balancer).
# Google provisions and manages the TLS certificate for the mapped domain, and
# the IAP policy on the service gates access to it.
#
# 🎨 TEMPLATE POST-EJECT: Verify the organization domain (medusa.software) once
# as a domain property in Search Console (https://search.google.com/search-console).
# A verified root domain covers every <subdomain>.medusa.software mapping, so
# individual subdomains do not need separate verification.
resource "google_cloud_run_domain_mapping" "web" {
  name     = local.counter_web_host_name
  location = module.common.gcp_primary_location
  project  = var.gcp_project_id

  metadata {
    namespace = var.gcp_project_id
  }

  spec {
    route_name = google_cloud_run_v2_service.primary.name
  }
}

output "web_url" {
  description = "Public URL of the web app."
  value       = "https://${local.counter_web_host_name}"
}
