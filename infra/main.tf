# Configuration

terraform {
  required_version = ">= 1.14"

  backend "gcs" {
    bucket = "ms-tfstate-c1984596bdabf023"
    prefix = "projects/counter/v2/root"
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
  source = "./common"
}

# Providers

# Primary Google provider
provider "google" {
  project = module.common.gcp_meta_project_id
  region  = module.common.gcp_primary_location
}

# Secondary Google provider
provider "google" {
  alias   = "orgpolicy"
  project = module.common.gcp_meta_project_id
  region  = module.common.gcp_primary_location

  # Allow overriding the project for organization policy resources
  user_project_override = true
  billing_project       = local.gcp_project_id
}

# GitHub provider for accessing CI/CD variables

variable "gh_token" {
  description = "Organization-owned GitHub token."
  type        = string
  sensitive   = true
}

provider "github" {
  owner = module.common.gh_organization_name
  token = var.gh_token
}
