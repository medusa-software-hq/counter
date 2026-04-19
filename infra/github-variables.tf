# Actions variables consumed by CI/CD jobs

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
