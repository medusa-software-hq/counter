# Inputs

variable "gh_token" {
  description = "Organization-owned GitHub token."
  type        = string
  sensitive   = true
}

# Provider

provider "github" {
  owner = module.common.gh_organization_name
  token = var.gh_token
}

# Resources

# Has to be imported:
# terraform import github_repository.app_repo $GH_APP_REPO_NAME
resource "github_repository" "this" {
  name       = module.common.gh_repo_name
  visibility = "private"

  has_discussions = false
  has_issues      = false
  has_projects    = false
  has_wiki        = false

  allow_merge_commit = true
  allow_squash_merge = false
  allow_rebase_merge = false

  allow_forking          = true
  allow_auto_merge       = true
  delete_branch_on_merge = true
}

locals {
  # Discovered manually
  gh_actions_integration_id = 15368
}

resource "github_repository_ruleset" "default_branch" {
  name        = "Default branch"
  repository  = github_repository.this.name
  target      = "branch"
  enforcement = "active"

  conditions {
    ref_name {
      include = ["~DEFAULT_BRANCH"]
      exclude = []
    }
  }

  rules {
    creation                = true
    update                  = false
    deletion                = true
    required_linear_history = false
    required_signatures     = true
    non_fast_forward        = true # Block force pushes

    pull_request {
      allowed_merge_methods = ["merge"]
    }

    required_status_checks {
      required_check {
        context        = "pre-commit"
        integration_id = local.gh_actions_integration_id
      }

      required_check {
        context        = "Build (counter-web)"
        integration_id = local.gh_actions_integration_id
      }

      required_check {
        context        = "Terraform Plan (foundation)"
        integration_id = local.gh_actions_integration_id
      }

      strict_required_status_checks_policy = true
    }
  }
}

resource "github_actions_repository_permissions" "this" {
  repository      = github_repository.this.name
  enabled         = true
  allowed_actions = "all"
}

# Actions variables consumed by CI/CD jobs

resource "github_actions_variable" "gcp_project_id" {
  repository    = github_repository.this.name
  variable_name = "GCP_PROJECT_ID"
  value         = google_project.gcp_project.project_id
}

resource "github_actions_variable" "gcp_ci_cd_sa_email" {
  repository    = github_repository.this.name
  variable_name = "GCP_CI_CD_SA_EMAIL"
  value         = google_service_account.ci_cd_sa.email
}
