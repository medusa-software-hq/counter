# Configuration
#
# The Cloud Run domain mapping is split out from the web foundation because it
# must be applied by the shared org-level domain-mapper service account (the only
# identity that's a verified owner of the domain), not by this project's CI/CD SA.

terraform {
  required_version = ">= 1.14"

  backend "gcs" {
    bucket = "ms-tfstate-c1984596bdabf023"
    prefix = "projects/counter/baseline/apps/web/domain-mapping" # 🎨 TEMPLATE EJECT: Update the prefix (!)
  }

  required_providers {
    cloudflare = {
      source  = "cloudflare/cloudflare"
      version = "~> 5.18"
    }
  }
}

# Module imports

module "common" {
  source = "../../../infra/common"
}

# Providers

# Cloudflare provider for managing DNS records

variable "cloudflare_api_token" {
  description = "Cloudflare API token."
  type        = string
  sensitive   = true
}

provider "cloudflare" {
  api_token = var.cloudflare_api_token
}
