# One-time bootstrap of the account-wide AWS foundation the rest of the infra
# builds on: the Terraform state bucket, the GitHub Actions OIDC provider, and
# the CI/CD deploy role. Applied once, by an admin, with LOCAL state — it cannot
# keep its state in the bucket it creates. Every other root uses the S3 backend
# this bucket provides.

terraform {
  required_version = ">= 1.14"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

module "common" {
  source = "../common"
}

provider "aws" {
  region = local.aws_region
}

locals {
  aws_region     = "eu-central-1"
  aws_account_id = "682544514886"

  # Also hard-coded in every root's `backend "s3"` block, which cannot take a
  # variable or module output — keep the two in sync.
  tfstate_bucket_name = "ms-counter-tfstate-${local.aws_account_id}"

  name_prefix = "${module.common.project_base_name}-${module.common.project_variant}"
}
