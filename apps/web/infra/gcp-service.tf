# Dedicated service account for the Cloud Run frontend service.
resource "google_service_account" "primary_service_sa" {
  project      = var.gcp_project_id
  account_id   = "${module.common.gcp_web_run_service_name}-sa"
  display_name = "Cloud Run Service Account"
}

# The primary Cloud Run service for the web app
resource "google_cloud_run_v2_service" "primary" {
  project             = var.gcp_project_id
  name                = module.common.gcp_web_run_service_name
  location            = module.common.gcp_primary_location
  deletion_protection = false # This project is experimental
  ingress             = "INGRESS_TRAFFIC_INTERNAL_LOAD_BALANCER"

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
    # noinspection HILUnresolvedReference
    ignore_changes = [
      template[0].containers[0].image,
      client,
      client_version,
    ]
  }

  traffic {
    type    = "TRAFFIC_TARGET_ALLOCATION_TYPE_LATEST"
    percent = 100
  }
}

# Load balancing

resource "google_compute_region_network_endpoint_group" "primary_service_neg" {
  name                  = "primary-neg"
  network_endpoint_type = "SERVERLESS"
  region                = module.common.gcp_primary_location

  cloud_run {
    service = google_cloud_run_v2_service.primary.name
  }
}

resource "google_compute_backend_service" "primary_service_compute_backend" {
  name                  = "primary-service-compute-backend"
  load_balancing_scheme = "EXTERNAL_MANAGED"
  protocol              = "HTTP"

  backend {
    group = google_compute_region_network_endpoint_group.primary_service_neg.id
  }

  iap {
    enabled               = true
  }
}

output "cloud_run_primary_service_url" {
  value = google_cloud_run_v2_service.primary.uri
}
