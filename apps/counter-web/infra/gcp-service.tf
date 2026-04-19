# Dedicated service account for the Cloud Run frontend service.
resource "google_service_account" "primary_service_sa" {
  project      = var.gcp_project_id
  account_id   = "${module.common.gcp_counter_web_run_service_name}-sa"
  display_name = "Cloud Run Service Account"
}

# The primary Cloud Run service for the app
resource "google_cloud_run_v2_service" "primary" {
  name                = module.common.gcp_counter_web_run_service_name
  location            = module.common.gcp_primary_location
  deletion_protection = false # This project is experimental
  ingress             = "INGRESS_TRAFFIC_ALL"

  template {
    service_account = google_service_account.primary_service_sa.email

    containers {
      # Initial placeholder; CI/CD will deploy the real image from Artifact Registry.
      image = "us-docker.pkg.dev/cloudrun/container/hello"

      ports {
        container_port = 8080
      }
    }
  }

  # The image is managed by CI/CD after initial creation.
  lifecycle {
    ignore_changes = [template[0].containers[0].image]
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
