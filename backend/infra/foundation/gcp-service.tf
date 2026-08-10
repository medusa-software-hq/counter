# Dedicated service account for the Cloud Run service.
resource "google_service_account" "primary_service_sa" {
  project      = var.gcp_project_id
  account_id   = "${module.common.gcp_api_run_service_name}-sa"
  display_name = "Cloud Run Service Account"
}

# The primary Cloud Run service for the app
resource "google_cloud_run_v2_service" "primary" {
  project             = var.gcp_project_id
  name                = module.common.gcp_api_run_service_name
  location            = module.common.gcp_primary_location
  deletion_protection = false # This project is experimental
  ingress             = "INGRESS_TRAFFIC_ALL"
  iap_enabled         = true

  template {
    service_account = google_service_account.primary_service_sa.email

    containers {
      # Initial placeholder; CI/CD will deploy the real image from Artifact Registry.
      image = "us-docker.pkg.dev/cloudrun/container/hello"

      ports {
        container_port = 8080
      }

      env {
        name  = "GOOGLE_WEB_CLIENT_ID"
        value = module.common.google_web_client_id
      }

      # Transitional duplicate: the previously-deployed image still reads GOOGLE_CLIENT_ID.
      # Keeping both names present makes the rename deploy safely across the non-atomic
      # Terraform-env vs. separate-image-deploy split (see Farm's trunk break). Remove in a
      # follow-up once the new image (which reads GOOGLE_WEB_CLIENT_ID) is deployed everywhere.
      env {
        name  = "GOOGLE_CLIENT_ID"
        value = module.common.google_web_client_id
      }

      # Also accept ID tokens minted by the CLI's Desktop OAuth client, so
      # `ms-counter` can call the API (see GoogleIdTokenAuthDecorator).
      env {
        name  = "GOOGLE_CLI_CLIENT_ID"
        value = module.common.google_cli_client_id
      }

      env {
        name  = "GOOGLE_ALLOWED_DOMAIN"
        value = module.common.organization_domain
      }

      env {
        name  = "CORS_ALLOWED_ORIGIN_REGEX"
        value = "https://[a-z0-9-]+\\.medusa\\.software"
      }

      env {
        name  = "IAP_API_AUDIENCE"
        value = local.iap_api_audience
      }

      env {
        name  = "TRUSTED_PROXY_EMAILS"
        value = "${module.common.gcp_web_run_service_name}-sa@${var.gcp_project_id}.iam.gserviceaccount.com"
      }

      env {
        name = "DATABASE_URL"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.database_url.secret_id
            version = "latest"
          }
        }
      }
    }
  }

  depends_on = [google_secret_manager_secret_version.database_url]

  # The image is managed by CI/CD after initial creation.
  # Env vars are managed by Terraform and must not be overwritten by deploys.
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

data "google_project" "this" {
  project_id = var.gcp_project_id
}

locals {
  # The API's IAP JWT-assertion audience, per environment. Empty until discovered from the enabled
  # IAP (see the decorator's assertion-audience diagnostic); while empty the gate rejects assertions.
  iap_api_audience = {
    prod    = ""
    staging = ""
  }[module.common.environment]
}

# IAP fronts the service; only the IAP service agent may invoke it (no public access).
resource "google_cloud_run_v2_service_iam_member" "iap_invoker" {
  project  = google_cloud_run_v2_service.primary.project
  location = google_cloud_run_v2_service.primary.location
  name     = google_cloud_run_v2_service.primary.name
  role     = "roles/run.invoker"
  member   = "serviceAccount:service-${data.google_project.this.number}@gcp-sa-iap.iam.gserviceaccount.com"
}

# Who may pass IAP: the web service's SA (the same-origin proxy hop) and org-domain users (the CLI).
# The IAP web-resource IAM requires the project NUMBER, not id (provider issue #23092).
resource "google_iap_web_cloud_run_service_iam_member" "proxy_accessor" {
  project                = data.google_project.this.number
  location               = google_cloud_run_v2_service.primary.location
  cloud_run_service_name = google_cloud_run_v2_service.primary.name
  role                   = "roles/iap.httpsResourceAccessor"
  member                 = "serviceAccount:${module.common.gcp_web_run_service_name}-sa@${var.gcp_project_id}.iam.gserviceaccount.com"
}

resource "google_iap_web_cloud_run_service_iam_member" "domain_accessor" {
  project                = data.google_project.this.number
  location               = google_cloud_run_v2_service.primary.location
  cloud_run_service_name = google_cloud_run_v2_service.primary.name
  role                   = "roles/iap.httpsResourceAccessor"
  member                 = "domain:${module.common.organization_domain}"
}


output "cloud_run_primary_service_url" {
  value = google_cloud_run_v2_service.primary.uri
}
