terraform {
  required_version = ">= 1.14"
}

locals {
  organization_domain         = "medusa.software"
  gcp_organization_prefix     = "ms"
  gcp_primary_location        = "europe-west1"
  terraform_state_bucket_name = "ms-tfstate-f1cbea4f18df7b19"

  gh_organization_name = "medusa-software-hq"
  gh_repo_name         = "counter"

  project_base_name = "counter"
  project_variant   = "base"
}

output "organization_domain" {
  value = local.organization_domain
}

output "gcp_organization_prefix" {
  value = local.gcp_organization_prefix
}

output "gcp_primary_location" {
  value = local.gcp_primary_location
}

output "terraform_state_bucket_name" {
  value = local.terraform_state_bucket_name
}

output "gh_organization_name" {
  value = local.gh_organization_name
}

output "gh_repo_name" {
  value = local.gh_repo_name
}

output "project_base_name" {
  value = local.project_base_name
}

output "project_variant" {
  value = local.project_variant
}
