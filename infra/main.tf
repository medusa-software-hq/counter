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
  project               = module.common.gcp_meta_project_id
  region                = module.common.gcp_primary_location
  user_project_override = true
  billing_project       = module.common.gcp_meta_project_id
}

# GitHub provider for accessing CI/CD variables

provider "github" {
  owner = module.common.gh_organization_name

  app_auth {
    # Use the environment variables:
    # GITHUB_APP_ID, GITHUB_APP_INSTALLATION_ID, GITHUB_APP_PEM_FILE
  }
}
