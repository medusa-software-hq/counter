# Actions variables consumed by CI/CD jobs

data "terraform_remote_state" "counter_service_infra" {
  backend = "gcs"

  config = {
    bucket = module.common.gcp_terraform_state_bucket_name
    prefix = "projects/counter/v2/counter-service/foundation"
  }
}

resource "github_actions_variable" "gcp_project_id" {
  repository    = github_repository.this.name
  variable_name = "GCP_PROJECT_ID"
  value         = google_project.gcp_project.project_id
}

resource "github_actions_variable" "gcp_primary_location" {
  repository    = github_repository.this.name
  variable_name = "GCP_PRIMARY_LOCATION"
  value         = module.common.gcp_primary_location
}

resource "github_actions_variable" "gcp_counter_service_run_service_name" {
  repository    = github_repository.this.name
  variable_name = "GCP_COUNTER_SERVICE_RUN_SERVICE_NAME"
  value         = module.common.gcp_counter_service_run_service_name
}

resource "github_actions_variable" "gcp_counter_web_service_name" {
  repository    = github_repository.this.name
  variable_name = "GCP_COUNTER_WEB_RUN_SERVICE_NAME"
  value         = module.common.gcp_counter_web_run_service_name
}

resource "github_actions_variable" "gcp_cicd_sa_email" {
  repository    = github_repository.this.name
  variable_name = "GCP_CICD_SA_EMAIL"
  value         = google_service_account.cicd_sa.email
}

resource "github_actions_variable" "gcp_ar_repo_hostname" {
  repository    = github_repository.this.name
  variable_name = "GCP_AR_REPO_HOSTNAME"
  value         = split("/", google_artifact_registry_repository.primary.registry_uri)[0]
}

resource "github_actions_variable" "gcp_ar_repo_endpoint" {
  repository    = github_repository.this.name
  variable_name = "GCP_AR_REPO_ENDPOINT"
  value         = local.gcp_ar_repo_endpoint
}

resource "github_actions_variable" "gcp_counter_service_url" {
  repository    = github_repository.this.name
  variable_name = "GCP_COUNTER_SERVICE_URL"
  value         = data.terraform_remote_state.counter_service_infra.outputs.cloud_run_primary_service_url
}

# Consumed by the counter-web frontend build (baked into the JS bundle).
resource "github_actions_variable" "google_client_id" {
  repository    = github_repository.this.name
  variable_name = "GOOGLE_CLIENT_ID"
  value         = module.common.google_client_id
}

resource "github_actions_variable" "google_allowed_domain" {
  repository    = github_repository.this.name
  variable_name = "GOOGLE_ALLOWED_DOMAIN"
  value         = module.common.organization_domain
}
