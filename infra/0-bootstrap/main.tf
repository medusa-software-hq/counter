# Configuration

terraform {
  required_version = ">= 1.14"

  backend "gcs" {
    bucket = "ms-tfstate-f1cbea4f18df7b19"
    prefix = "projects/counter/base/bootstrap"
  }

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 7.25"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.8"
    }
    github = {
      source  = "integrations/github"
      version = "~> 6.11"
    }
  }
}

# Module imports

module "common" {
  source = "../modules/common"
}

# %% Remote shared foundation state %%

data "terraform_remote_state" "shared_foundation_state" {
  backend = "gcs"

  config = {
    bucket = module.common.terraform_state_bucket_name
    prefix = "shared/foundation"
  }
}

locals {
  gcp_github_workload_identity_pool_name = data.terraform_remote_state.shared_foundation_state.outputs.gcp_github_workload_identity_pool_name
}

# %% App GCP project %%

data "google_organization" "gcp_organization" {
  domain = module.common.organization_domain
}

data "google_billing_account" "gcp_billing_account" {
  display_name = "My Billing Account"
  open         = true
}

resource "random_id" "gcp_project_random_id" {
  byte_length = 4
}

locals {
  gcp_project_id = "${module.common.gcp_organization_prefix}-${module.common.project_base_name}-${random_id.gcp_project_random_id.hex}"
}

resource "google_project" "gcp_project" {
  org_id          = data.google_organization.gcp_organization.org_id
  billing_account = data.google_billing_account.gcp_billing_account.id

  name       = "${module.common.project_base_name} - ${module.common.project_variant}"
  project_id = local.gcp_project_id

  auto_create_network = false
}

provider "google" {
  project = local.gcp_project_id
  region  = module.common.gcp_primary_location
}

# Separate provider alias with user_project_override for orgpolicy.googleapis.com,
# which requires a quota project when using ADC (user credentials).
provider "google" {
  alias                 = "orgpolicy"
  project               = local.gcp_project_id
  region                = module.common.gcp_primary_location
  user_project_override = true
  billing_project       = local.gcp_project_id
}

# Enable required GCP APIs
resource "google_project_service" "apis" {
  for_each = toset([
    "iam.googleapis.com",
    "iamcredentials.googleapis.com",
    "cloudresourcemanager.googleapis.com",
    "storage.googleapis.com",
    "compute.googleapis.com",
    "serviceusage.googleapis.com",
    "run.googleapis.com",
    "iap.googleapis.com",
    "orgpolicy.googleapis.com",
  ])

  project            = google_project.gcp_project.id
  service            = each.key
  disable_on_destroy = false
}

# %% GitHub Actions service account %%

resource "google_service_account" "ci_cd_sa" {
  project      = local.gcp_project_id
  account_id   = "github-actions"
  display_name = "GitHub Actions Service Account"

  depends_on = [google_project_service.apis["iam.googleapis.com"]]
}

# Allow GitHub Actions to impersonate the service account via WIF
resource "google_service_account_iam_member" "ci_cd_sa_wi_user" {
  service_account_id = google_service_account.ci_cd_sa.name
  role               = "roles/iam.workloadIdentityUser"
  member             = "principalSet://iam.googleapis.com/${local.gcp_github_workload_identity_pool_name}/attribute.repository/${module.common.gh_organization_name}/${module.common.gh_repo_name}"
}

# Grant GitHub Actions SA read access to all Terraform state
resource "google_storage_bucket_iam_member" "ci_cd_sa_object_viewer" {
  bucket = module.common.terraform_state_bucket_name
  role   = "roles/storage.objectViewer"
  member = "serviceAccount:${google_service_account.ci_cd_sa.email}"
}

# Grant GitHub Actions SA write access to this project's Terraform state prefix
resource "google_storage_bucket_iam_member" "ci_cd_sa_object_admin" {
  bucket = module.common.terraform_state_bucket_name
  role   = "roles/storage.objectAdmin"
  member = "serviceAccount:${google_service_account.ci_cd_sa.email}"

  condition {
    title       = "project_prefix_only"
    description = "Allow read/write access to objects in the project prefix"
    expression  = "resource.name.startsWith('projects/_/buckets/${module.common.terraform_state_bucket_name}/objects/projects/${module.common.project_base_name}/${module.common.project_variant}/')"
  }
}

# Grant GitHub Actions SA storage admin on the GCP project (create buckets, upload files)
resource "google_project_iam_member" "ci_cd_sa_storage_admin" {
  project = local.gcp_project_id
  role    = "roles/storage.admin"
  member  = "serviceAccount:${google_service_account.ci_cd_sa.email}"
}

# Grant GitHub Actions SA compute admin on the GCP project (manage ALB, URL maps, SSL certs)
resource "google_project_iam_member" "ci_cd_sa_compute_admin" {
  project = local.gcp_project_id
  role    = "roles/compute.admin"
  member  = "serviceAccount:${google_service_account.ci_cd_sa.email}"
}

# Grant GitHub Actions SA Cloud Run admin (deploy Cloud Run services)
resource "google_project_iam_member" "ci_cd_sa_run_admin" {
  project = local.gcp_project_id
  role    = "roles/run.admin"
  member  = "serviceAccount:${google_service_account.ci_cd_sa.email}"
}

# Grant GitHub Actions SA IAP admin (manage IAP access policies)
resource "google_project_iam_member" "ci_cd_sa_iap_admin" {
  project = local.gcp_project_id
  role    = "roles/iap.admin"
  member  = "serviceAccount:${google_service_account.ci_cd_sa.email}"
}

# Grant GitHub Actions SA service account admin (create/manage Cloud Run SAs)
resource "google_project_iam_member" "ci_cd_sa_sa_admin" {
  project = local.gcp_project_id
  role    = "roles/iam.serviceAccountAdmin"
  member  = "serviceAccount:${google_service_account.ci_cd_sa.email}"
}

# Override the org-level iam.allowedPolicyMemberDomains constraint at the project level
# to allow allUsers on the public assets bucket (CSS, images).
resource "google_org_policy_policy" "allow_all_iam_members" {
  provider = google.orgpolicy

  name   = "projects/${local.gcp_project_id}/policies/iam.allowedPolicyMemberDomains"
  parent = "projects/${local.gcp_project_id}"

  spec {
    rules {
      allow_all = "TRUE"
    }
  }

  depends_on = [google_project_service.apis["orgpolicy.googleapis.com"]]
}

# %% Outputs %%

output "gcp_project_id" {
  description = "GCP project ID."
  value       = local.gcp_project_id
}

output "gcp_ci_cd_sa_email" {
  description = "GCP CI/CD service account e-mail."
  value       = google_service_account.ci_cd_sa.email
}
