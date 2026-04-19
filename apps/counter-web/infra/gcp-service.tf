# Dedicated service account for the Cloud Run frontend service.
resource "google_service_account" "primary_service_sa" {
  project      = var.gcp_project_id
  account_id   = "${local.counter_web_prefix}-sa"
  display_name = "Cloud Run Service Account"
}

# The primary Cloud Run service for the app
resource "google_cloud_run_v2_service" "primary" {
  name                = local.counter_web_prefix
  location            = module.common.gcp_primary_location
  deletion_protection = false # This project is experimental
  ingress             = "INGRESS_TRAFFIC_ALL"

  template {
    service_account = google_service_account.primary_service_sa.email

    containers {
      # Placeholder image
      image = "us-docker.pkg.dev/cloudrun/container/hello"

      ports {
        container_port = 8080
      }
    }
  }

  traffic {
    type    = "TRAFFIC_TARGET_ALLOCATION_TYPE_LATEST"
    percent = 100
  }
}

resource "google_cloud_run_service_iam_member" "primary_service_public_access" {
  location = google_cloud_run_v2_service.primary.location
  project  = google_cloud_run_v2_service.primary.project
  service  = google_cloud_run_v2_service.primary.name

  role   = "roles/run.invoker"
  member = "allUsers"
}

output "cloud_run_primary_service_url" {
  value = google_cloud_run_v2_service.primary.uri
}
