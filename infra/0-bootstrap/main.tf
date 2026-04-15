# Configuration

terraform {
  required_version = ">= 1.14"

  backend "gcs" {
    bucket = "ms-tfstate-f1cbea4f18df7b19"
    prefix = "projects/counter/base/bootstrap"
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
  source = "../modules/common"
}
