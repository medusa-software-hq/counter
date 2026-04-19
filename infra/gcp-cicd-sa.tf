# Read shared Terraform State

data "terraform_remote_state" "shared" {
  backend = "gcs"

  config = {
    bucket = module.common.gcp_terraform_state_bucket_name
    prefix = "shared/foundation"
  }
}

locals {
  gcp_cicd_wi_pool_name = data.terraform_remote_state.shared.outputs.gcp_cicd_wi_pool_name
}

# CI/CD service account

resource "google_service_account" "cicd_sa" {
  project      = local.gcp_project_id
  account_id   = "github-actions"
  display_name = "CI/CD Service Account"

  depends_on = [google_project_service.apis["iam.googleapis.com"]]
}

# Allow GitHub Actions to impersonate the CI/CD SA via WIF
resource "google_service_account_iam_member" "cicd_sa_wi_user" {
  service_account_id = google_service_account.cicd_sa.name
  role               = "roles/iam.workloadIdentityUser"
  member             = "principalSet://iam.googleapis.com/${local.gcp_cicd_wi_pool_name}/attribute.repository/${module.common.gh_organization_name}/${module.common.gh_repo_name}"
}

# Grant CI/CD SA read access to all Terraform state
resource "google_storage_bucket_iam_member" "cicd_sa_object_viewer" {
  bucket = module.common.gcp_terraform_state_bucket_name
  role   = "roles/storage.objectViewer"
  member = "serviceAccount:${google_service_account.cicd_sa.email}"
}

# Grant CI/CD SA write access to this project's Terraform state prefix
resource "google_storage_bucket_iam_member" "cicd_sa_object_admin" {
  bucket = module.common.gcp_terraform_state_bucket_name
  role   = "roles/storage.objectAdmin"
  member = "serviceAccount:${google_service_account.cicd_sa.email}"

  condition {
    title       = "project_prefix_only"
    description = "Allow read/write access to objects in the project prefix"
    expression  = "resource.name.startsWith('projects/_/buckets/${module.common.gcp_terraform_state_bucket_name}/objects/projects/${module.common.project_base_name}/${module.common.project_variant}/')"
  }
}

# Grant CI/CD SA read access to this project (required for terraform plan)
resource "google_project_iam_member" "cicd_sa_project_viewer" {
  project = local.gcp_project_id
  role    = "roles/viewer"
  member  = "serviceAccount:${google_service_account.cicd_sa.email}"
}

# Grant CI/CD SA permission to use meta project quota (required for user_project_override)
resource "google_project_iam_member" "cicd_sa_meta_service_usage_consumer" {
  project = module.common.gcp_meta_project_id
  role    = "roles/serviceusage.serviceUsageConsumer"
  member  = "serviceAccount:${google_service_account.cicd_sa.email}"
}

# Grant CI/CD SA permission to read GCP organization (required for terraform plan)
resource "google_organization_iam_member" "cicd_sa_org_viewer" {
  org_id = data.google_organization.gcp_organization.org_id
  role   = "roles/resourcemanager.organizationViewer"
  member = "serviceAccount:${google_service_account.cicd_sa.email}"
}

# Grant CI/CD SA permission to read billing accounts (required for terraform plan)
resource "google_billing_account_iam_member" "cicd_sa_billing_viewer" {
  billing_account_id = data.google_billing_account.gcp_billing_account.id
  role               = "roles/billing.viewer"
  member             = "serviceAccount:${google_service_account.cicd_sa.email}"
}

# Outputs

output "gcp_cicd_sa_email" {
  description = "GCP CI/CD service account e-mail."
  value       = google_service_account.cicd_sa.email
}
