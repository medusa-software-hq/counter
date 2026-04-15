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
    cloudflare = {
      source  = "cloudflare/cloudflare"
      version = "~> 5.18"
    }
    google-beta = {
      source  = "hashicorp/google-beta"
      version = "~> 7.25"
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

variable "cloudflare_api_token" {
  description = "Cloudflare API token with DNS edit permissions for the medusa.software zone."
  type        = string
  sensitive   = true
}

variable "cloudflare_zone_id" {
  description = "Cloudflare zone ID for medusa.software."
  type        = string
}

# %% Providers %%

provider "google" {
  project = var.gcp_project_id
  region  = module.common.gcp_primary_location
}

provider "google-beta" {
  project = var.gcp_project_id
  region  = module.common.gcp_primary_location
}

provider "cloudflare" {
  api_token = var.cloudflare_api_token
}

# %% Frontend Cloud Storage buckets %%

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

# Grant Cloud CDN's fill service account read access to both buckets.
# google_compute_backend_bucket uses this identity to fetch objects from private buckets.
resource "google_project_service_identity" "cloud_cdn_sa" {
  provider = google-beta
  project  = var.gcp_project_id
  service  = "compute.googleapis.com"
}

resource "google_storage_bucket_iam_member" "assets_bucket_cdn_reader" {
  bucket = google_storage_bucket.assets_bucket.name
  role   = "roles/storage.objectViewer"
  member = "serviceAccount:${google_project_service_identity.cloud_cdn_sa.email}"
}

resource "google_storage_bucket_iam_member" "root_bucket_cdn_reader" {
  bucket = google_storage_bucket.root_bucket.name
  role   = "roles/storage.objectViewer"
  member = "serviceAccount:${google_project_service_identity.cloud_cdn_sa.email}"
}

# %% Backend: Cloud Storage buckets served via Cloud CDN %%

locals {
  ttl_zero = 0
  ttl_1y   = 31536000 # 1 year in seconds
}

# Backend bucket for Vite content-hashed assets (/assets/*).
# FORCE_CACHE_ALL overrides any GCS object metadata — safe here because all objects
# in this bucket are immutable (new deploy = new filename).
resource "google_compute_backend_bucket" "assets_backend" {
  name        = "${module.common.project_base_name}-assets-backend"
  bucket_name = google_storage_bucket.assets_bucket.name
  enable_cdn  = true

  cdn_policy {
    cache_mode = "FORCE_CACHE_ALL"

    # Safe because filenames are content-hashed — a new deploy produces new filenames.
    default_ttl = local.ttl_1y
    max_ttl     = local.ttl_1y
    client_ttl  = local.ttl_1y

    # Serve stale content for up to 1 day if the origin is unavailable.
    serve_while_stale = 86400

    negative_caching = true
  }
}

# Backend bucket for root files (index.html, etc.).
# These reference hashed asset filenames and must never be served stale.
# FORCE_CACHE_ALL with zero TTLs means the CDN revalidates on every request.
resource "google_compute_backend_bucket" "root_backend" {
  name        = "${module.common.project_base_name}-root-backend"
  bucket_name = google_storage_bucket.root_bucket.name
  enable_cdn  = true

  cdn_policy {
    cache_mode  = "FORCE_CACHE_ALL"
    default_ttl = local.ttl_zero
    max_ttl     = local.ttl_zero
    client_ttl  = local.ttl_zero

    negative_caching = true
  }
}

# %% External Application Load Balancer %%

resource "google_compute_global_address" "alb_ip" {
  name = "${module.common.project_base_name}-alb-ip"
}

resource "google_compute_managed_ssl_certificate" "cert" {
  name = "${module.common.project_base_name}-cert"

  managed {
    domains = ["${module.common.project_base_name}.${module.common.organization_domain}"]
  }
}

resource "google_compute_url_map" "url_map" {
  name            = "${module.common.project_base_name}-url-map"
  default_service = google_compute_backend_bucket.root_backend.id

  path_matcher {
    name            = "paths"
    default_service = google_compute_backend_bucket.root_backend.id

    # Route Vite content-hashed assets to the assets bucket (long CDN cache).
    path_rule {
      paths   = ["/assets/*"]
      service = google_compute_backend_bucket.assets_backend.id
    }
  }

  host_rule {
    hosts        = ["${module.common.project_base_name}.${module.common.organization_domain}"]
    path_matcher = "paths"
  }
}

resource "google_compute_target_https_proxy" "https_proxy" {
  name             = "${module.common.project_base_name}-https-proxy"
  url_map          = google_compute_url_map.url_map.id
  ssl_certificates = [google_compute_managed_ssl_certificate.cert.id]
}

resource "google_compute_global_forwarding_rule" "forwarding_rule" {
  name                  = "${module.common.project_base_name}-forwarding-rule"
  load_balancing_scheme = "EXTERNAL_MANAGED"
  target                = google_compute_target_https_proxy.https_proxy.id
  ip_address            = google_compute_global_address.alb_ip.id
  port_range            = "443"
}

# %% DNS %%

resource "cloudflare_dns_record" "app_dns" {
  zone_id = var.cloudflare_zone_id
  type    = "A"
  name    = module.common.project_base_name
  content = google_compute_global_address.alb_ip.address
  ttl     = 3600

  # Keep proxying off so GCP-managed SSL certificate provisioning (ACME HTTP-01 challenge
  # directly to the IP) works correctly.
  proxied = false
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

output "load_balancer_ip" {
  description = "Static IP address of the Application Load Balancer."
  value       = google_compute_global_address.alb_ip.address
}
