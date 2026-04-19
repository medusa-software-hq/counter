# Actions variables consumed by CI/CD jobs

resource "github_actions_variable" "gcp_project_id" {
  repository    = github_repository.this.name
  variable_name = "GCP_PROJECT_ID"
  value         = google_project.gcp_project.project_id
}

resource "github_actions_variable" "gcp_primary_location" {
  repository    = github_repository.this.name
  variable_name = "GCP_PIMARY_LOCATION"
  value         = module.common.gcp_primary_location
}

resource "github_actions_variable" "gcp_cicd_sa_email" {
  repository    = github_repository.this.name
  variable_name = "GCP_CICD_SA_EMAIL"
  value         = google_service_account.cicd_sa.email
}
