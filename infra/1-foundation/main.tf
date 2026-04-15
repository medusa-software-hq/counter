# %% Configuration %%

terraform {
  required_version = ">= 1.14"

  backend "gcs" {
    bucket = "ms-tfstate-f1cbea4f18df7b19"
    prefix = "projects/counter/base/foundation"
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

module "common" {
  source = "../modules/common"
}

# %% Inputs %%

variable "gcp_project_id" {
  description = "GCP project ID."
  type        = string
}

# %% App GCP project %%

provider "google" {
  project = var.gcp_project_id
  region  = module.common.gcp_primary_location
}

# %%% Frontend Cloud Storage buckets %%%

resource "random_id" "assets_bucket_random_id" {
  byte_length = 4
}

resource "random_id" "root_bucket_random_id" {
  byte_length = 4
}

# Vite content-hashed files (e.g. index-CWPHXiaZ.js) under /assets/*. Long-lived, immutable files.
resource "google_storage_bucket" "assets_bucket" {
  project                     = var.gcp_project_id
  name                        = "${module.common.project_base_name}-assets-${random_id.assets_bucket_random_id.hex}"
  location                    = module.common.gcp_primary_location
  uniform_bucket_level_access = true
  force_destroy               = true # This project is experimental
}

# Root files bucket — index.html and other short-lived entry-point files.
resource "google_storage_bucket" "root_bucket" {
  project                     = var.gcp_project_id
  name                        = "${module.common.project_base_name}-root-${random_id.root_bucket_random_id.hex}"
  location                    = module.common.gcp_primary_location
  uniform_bucket_level_access = true
  force_destroy               = true # This project is experimental

  website {
    # Serve index.html for the bucket root
    main_page_suffix = "index.html"
    # Return index.html for any path not found in the bucket (SPA client-side routing fallback)
    not_found_page = "index.html"
  }
}

# %% Outputs %%

output "assets_bucket_name" {
  description = "Name of the Cloud Storage bucket holding Vite content-hashed frontend assets (/assets/*)."
  value       = google_storage_bucket.assets_bucket.name
}

output "root_bucket_name" {
  description = "Name of the Cloud Storage bucket holding root frontend files (index.html, etc.)."
  value       = google_storage_bucket.root_bucket.name
}
