terraform {
  required_version = ">= 1.14"
}

locals {
  gh_organization_name = "medusa-software-hq"
  gh_app_repo_name     = "counter"
}

output "gh_organization_name" {
  value = local.gh_organization_name
}

output "gh_app_repo_name" {
  value = local.gh_app_repo_name
}
