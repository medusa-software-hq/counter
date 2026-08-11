# Configuration

terraform {
  required_version = ">= 1.14"

  # State bucket is created by infra/bootstrap; the name is hard-coded there too
  # (backend blocks take no variables). The default workspace (prod) keys at
  # `root/…`; other workspaces get an `env:/<workspace>/` prefix.
  backend "s3" {
    bucket       = "ms-counter-tfstate-682544514886"
    key          = "root/terraform.tfstate"
    region       = "eu-central-1"
    encrypt      = true
    use_lockfile = true
  }

  required_providers {
    github = {
      source  = "integrations/github"
      version = "~> 6.11"
    }
  }
}

# Module imports

module "common" {
  source = "./common"
}

# Providers

# GitHub provider for writing CI/CD Actions variables. The repository itself is
# managed by the .github/config root; here it is only referenced as data.

variable "gh_token" {
  description = "Organization-owned GitHub token."
  type        = string
  sensitive   = true
}

provider "github" {
  owner = module.common.gh_organization_name
  token = var.gh_token
}

data "github_repository" "this" {
  full_name = "${module.common.gh_organization_name}/${module.common.gh_repo_name}"
}
