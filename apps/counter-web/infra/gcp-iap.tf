# Ensure the IAP managed service agent exists for this project.
resource "google_project_service_identity" "iap_sa" {
  provider = google-beta
  project  = var.gcp_project_id
  service  = "iap.googleapis.com"
}

# Allow the IAP managed service agent to invoke the Cloud Run frontend service.
resource "google_cloud_run_v2_service_iam_member" "run_invoker_iap" {
  project  = var.gcp_project_id
  location = google_cloud_run_v2_service.primary.location
  name     = google_cloud_run_v2_service.primary.name
  role     = "roles/run.invoker"
  member   = google_project_service_identity.iap_sa.member
}

# Access
resource "google_iap_web_backend_service_iam_member" "domain_access" {
  project             = var.gcp_project_id
  web_backend_service = google_compute_backend_service.primary_service_compute_backend.name
  role                = "roles/iap.httpsResourceAccessor"
  member              = "domain:${module.common.organization_domain}"
}
