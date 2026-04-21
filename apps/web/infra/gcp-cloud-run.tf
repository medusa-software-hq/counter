resource "google_project_service_identity" "cloud_run_agent" {
  provider = google-beta
  project  = var.gcp_project_id
  service  = "run.googleapis.com"
}

data "google_project" "project" {
  project_id = var.gcp_project_id
}

# Allow the Cloud Run service agent to invoke the Cloud Run service
resource "google_cloud_run_v2_service_iam_member" "primary_service_invoker_alb" {
  project  = var.gcp_project_id
  location = module.common.gcp_primary_location
  name     = google_cloud_run_v2_service.primary.name
  role     = "roles/run.invoker"
  member   = google_project_service_identity.cloud_run_agent.member
}
