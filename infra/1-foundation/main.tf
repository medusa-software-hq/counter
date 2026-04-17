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
    google-beta = {
      source  = "hashicorp/google-beta"
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

# %% Project data %%

data "google_project" "project" {
  project_id = var.gcp_project_id
}

locals {
  ttl_1y   = 31536000 # 1 year in seconds
  ttl_auto = 1        # means "automatic" in Cloudflare

  # Cloud Run serverless robot SA — invokes Cloud Run on behalf of the ALB NEG
  # https://docs.cloud.google.com/iam/docs/service-agents#google-cloud-run-service-agent
  cloud_run_robot_sa = "serviceAccount:service-${data.google_project.project.number}@serverless-robot-prod.iam.gserviceaccount.com"
}

# %% Cloud Run: Counter Service (gRPC backend) %%

locals {
  counter_service_name = "counter-service"
}

# Dedicated service account for the Counter Service.
resource "google_service_account" "counter_service_sa" {
  project      = var.gcp_project_id
  account_id   = local.counter_service_name
  display_name = "Counter Service Cloud Run Service Account"
}

resource "google_cloud_run_v2_service" "counter_service" {
  name                = local.counter_service_name
  location            = module.common.gcp_primary_location
  deletion_protection = false # This project is experimental
  ingress             = "INGRESS_TRAFFIC_ALL"

  template {
    service_account = google_service_account.counter_service_sa.email

    containers {
      ports {
        container_port = 8080
      }

      env {
        name  = "CORS_ALLOWED_ORIGIN"
        value = "https://[a-z0-9-]+\\.medusa\\.software"
      }

      image = "us-docker.pkg.dev/cloudrun/container/hello"
    }
  }

  traffic {
    type    = "TRAFFIC_TARGET_ALLOCATION_TYPE_LATEST"
    percent = 100
  }

  # noinspection HILUnresolvedReference
  lifecycle {
    ignore_changes = [template[0].containers[0].image]
  }
}

# Allow unauthenticated invocations of the Counter Service.
resource "google_cloud_run_v2_service_iam_member" "counter_service_invoker_all_users" {
  project  = var.gcp_project_id
  location = module.common.gcp_primary_location
  name     = google_cloud_run_v2_service.counter_service.name
  role     = "roles/run.invoker"
  member   = "allUsers"
}

# %% Frontend Cloud Storage buckets %%

resource "random_id" "assets_bucket_random_id" {
  byte_length = 4
}

resource "random_id" "root_bucket_random_id" {
  byte_length = 4
}

# Public assets bucket — CSS, images. Served via Cloud CDN. JS is intentionally excluded
# (served IAP-protected via Cloud Run from the root bucket instead).
resource "google_storage_bucket" "assets_bucket" {
  project                     = var.gcp_project_id
  name                        = "${module.common.project_base_name}-assets-${random_id.assets_bucket_random_id.hex}"
  location                    = module.common.gcp_primary_location
  uniform_bucket_level_access = true
  force_destroy               = true # This project is experimental
}

# The assets bucket is public — the org-level iam.allowedPolicyMemberDomains constraint
# is overridden at the project level in 0-bootstrap to allow this.
resource "google_storage_bucket_iam_member" "assets_bucket_public_reader" {
  bucket = google_storage_bucket.assets_bucket.name
  role   = "roles/storage.objectViewer"
  member = "allUsers"
}

# Root bucket — index.html and JS bundles. Served IAP-protected via Cloud Run.
resource "google_storage_bucket" "root_bucket" {
  project                     = var.gcp_project_id
  name                        = "${module.common.project_base_name}-root-${random_id.root_bucket_random_id.hex}"
  location                    = module.common.gcp_primary_location
  uniform_bucket_level_access = true
  force_destroy               = true # This project is experimental
}

# %% Cloud Run: nginx serving root bucket via GCS volume mount %%

# Dedicated service account for the Cloud Run frontend service.
resource "google_service_account" "frontend_sa" {
  project      = var.gcp_project_id
  account_id   = "${module.common.project_base_name}-frontend"
  display_name = "Frontend Cloud Run Service Account"
}

# Grant the frontend SA read access to the root bucket.
resource "google_storage_bucket_iam_member" "root_bucket_run_reader" {
  bucket = google_storage_bucket.root_bucket.name
  role   = "roles/storage.objectViewer"
  member = "serviceAccount:${google_service_account.frontend_sa.email}"
}

# nginx config bucket — holds default.conf, managed by Terraform.
# Terraform interpolates the counter-service URL at apply time so the SPA
# receives it as a <meta> tag injected by nginx sub_filter.
resource "random_id" "nginx_config_bucket_random_id" {
  byte_length = 4
}

resource "google_storage_bucket" "nginx_config_bucket" {
  project                     = var.gcp_project_id
  name                        = "${module.common.project_base_name}-nginx-config-${random_id.nginx_config_bucket_random_id.hex}"
  location                    = module.common.gcp_primary_location
  uniform_bucket_level_access = true
  force_destroy               = true
}

resource "google_storage_bucket_iam_member" "nginx_config_bucket_run_reader" {
  bucket = google_storage_bucket.nginx_config_bucket.name
  role   = "roles/storage.objectViewer"
  member = "serviceAccount:${google_service_account.frontend_sa.email}"
}

resource "google_storage_bucket_object" "nginx_config" {
  name   = "default.conf"
  bucket = google_storage_bucket.nginx_config_bucket.name

  content = <<-EOT
    server {
      listen 80;

      # Inject the counter-service URL into index.html as a <meta> tag.
      # The placeholder <meta name="counter-service-url" content=""> is replaced at
      # request time — no rebuild or custom Docker image required.
      sub_filter '<meta name="counter-service-url" content="">'
                 '<meta name="counter-service-url" content="${google_cloud_run_v2_service.counter_service.uri}">';
      sub_filter_once on;

      location / {
        root /usr/share/nginx/html;
        try_files $uri $uri/ /index.html;
      }
    }
  EOT
}

# nginx serves the mounted root bucket (index.html + JS bundles) behind IAP.
# Gen2 execution environment is required for GCS volume mounts.
resource "google_cloud_run_v2_service" "frontend" {
  name                = "${module.common.project_base_name}-frontend"
  location            = module.common.gcp_primary_location
  deletion_protection = false # This project is experimental
  ingress             = "INGRESS_TRAFFIC_INTERNAL_LOAD_BALANCER"

  template {
    service_account       = google_service_account.frontend_sa.email
    execution_environment = "EXECUTION_ENVIRONMENT_GEN2"

    containers {
      image = "nginx:alpine"

      ports {
        container_port = 80 # Nginx default
      }

      volume_mounts {
        name       = "root-bucket"
        mount_path = "/usr/share/nginx/html"
      }

      volume_mounts {
        name       = "nginx-config"
        mount_path = "/etc/nginx/conf.d"
      }
    }

    volumes {
      name = "root-bucket"
      gcs {
        bucket    = google_storage_bucket.root_bucket.name
        read_only = true
      }
    }

    volumes {
      name = "nginx-config"
      gcs {
        bucket    = google_storage_bucket.nginx_config_bucket.name
        read_only = true
      }
    }
  }

  traffic {
    type    = "TRAFFIC_TARGET_ALLOCATION_TYPE_LATEST"
    percent = 100
  }
}

# %% IAP service agent %%

# Ensure the IAP managed service agent exists for this project.
# Equivalent to: gcloud beta services identity create --service=iap.googleapis.com
# The resource is a no-op on update/destroy, so it is safe to declare here.
resource "google_project_service_identity" "iap_sa" {
  provider = google-beta
  project  = var.gcp_project_id
  service  = "iap.googleapis.com"
}

# Allow the ALB serverless NEG to invoke the Cloud Run frontend service.
resource "google_cloud_run_v2_service_iam_member" "frontend_invoker_alb" {
  project  = var.gcp_project_id
  location = module.common.gcp_primary_location
  name     = google_cloud_run_v2_service.frontend.name
  role     = "roles/run.invoker"
  member   = local.cloud_run_robot_sa
}

# Allow the IAP managed service agent to invoke the Cloud Run frontend service.
resource "google_cloud_run_v2_service_iam_member" "frontend_invoker_iap" {
  project  = var.gcp_project_id
  location = module.common.gcp_primary_location
  name     = google_cloud_run_v2_service.frontend.name
  role     = "roles/run.invoker"
  member   = google_project_service_identity.iap_sa.member
}

# %% IAP %%

# %% External Application Load Balancer %%

resource "google_compute_global_address" "alb_ip" {
  name = "${module.common.project_base_name}-alb-ip"
}

resource "google_compute_managed_ssl_certificate" "cert" {
  # Named counter-cert-2 after the original counter-cert was manually recreated to
  # fix a FAILED_NOT_VISIBLE state. Can be renamed back to counter-cert once cert-2
  # is stable and state is clean.
  name = "${module.common.project_base_name}-cert-2"

  managed {
    domains = ["${module.common.project_base_name}.${module.common.organization_domain}"]
  }
}

# %%% Backend: assets bucket (CSS + images, public CDN) %%%

# Content-hashed CSS and images served via Cloud CDN with a 1-year TTL.
# JS bundles are intentionally excluded — they are served IAP-protected via Cloud Run.
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

# %%% Backend: Cloud Run frontend service (HTML + JS, IAP-protected) %%%

resource "google_compute_region_network_endpoint_group" "frontend_neg" {
  name                  = "${module.common.project_base_name}-frontend-neg"
  network_endpoint_type = "SERVERLESS"
  region                = module.common.gcp_primary_location

  cloud_run {
    service = google_cloud_run_v2_service.frontend.name
  }
}

resource "google_compute_backend_service" "frontend_backend" {
  name                  = "${module.common.project_base_name}-frontend-backend"
  load_balancing_scheme = "EXTERNAL_MANAGED"
  protocol              = "HTTPS"

  backend {
    group = google_compute_region_network_endpoint_group.frontend_neg.id
  }

  iap {
    enabled = true
  }
}

# Grant all org-domain users access through IAP.
resource "google_iap_web_backend_service_iam_member" "frontend_iap_org_users" {
  project             = var.gcp_project_id
  web_backend_service = google_compute_backend_service.frontend_backend.name
  role                = "roles/iap.httpsResourceAccessor"
  member              = "domain:${module.common.organization_domain}"
}

# %%% URL map %%%

resource "google_compute_url_map" "url_map" {
  name            = "${module.common.project_base_name}-url-map"
  default_service = google_compute_backend_service.frontend_backend.id

  host_rule {
    hosts        = ["${module.common.project_base_name}.${module.common.organization_domain}"]
    path_matcher = "paths"
  }

  path_matcher {
    name            = "paths"
    default_service = google_compute_backend_service.frontend_backend.id

    # CSS, images, fonts — public CDN, long cache.
    # JS is at /js/* (not /assets/*) so this rule never matches JS bundles.
    path_rule {
      paths   = ["/assets/*"]
      service = google_compute_backend_bucket.assets_backend.id
    }
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
  ttl     = local.ttl_auto

  # Keep proxying off so GCP-managed SSL certificate provisioning (ACME HTTP-01 challenge
  # directly to the IP) works correctly.
  proxied = false
}

# %% Outputs %%

output "counter_service_name" {
  description = "Name of the Counter Service."
  value       = google_cloud_run_v2_service.counter_service.name
}

output "counter_service_location" {
  description = "Location of the Counter Service."
  value       = google_cloud_run_v2_service.counter_service.location
}

output "counter_service_url" {
  description = "Cloud Run URL of the Counter Service."
  value       = google_cloud_run_v2_service.counter_service.uri
}

output "assets_bucket_name" {
  description = "Name of the Cloud Storage bucket holding public CSS and image assets."
  value       = google_storage_bucket.assets_bucket.name
}

output "root_bucket_name" {
  description = "Name of the Cloud Storage bucket holding IAP-protected HTML and JS bundles."
  value       = google_storage_bucket.root_bucket.name
}

output "load_balancer_ip" {
  description = "Static IP address of the Application Load Balancer."
  value       = google_compute_global_address.alb_ip.address
}
