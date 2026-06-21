# Configuration

terraform {
  required_version = ">= 1.14"

  backend "gcs" {
    bucket = "ms-tfstate-c1984596bdabf023"
    prefix = "projects/counter/v4/backend/api/foundation" # 🎨 TEMPLATE EJECT: Update the prefix (!)
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
  }
}

# Module imports

module "common" {
  source = "../../../infra/common"
}

# Providers

variable "gcp_project_id" {
  description = "GCP project ID."
  type        = string
}

# Primary Google provider
provider "google" {
  project = module.common.gcp_meta_project_id
  region  = module.common.gcp_primary_location
}

