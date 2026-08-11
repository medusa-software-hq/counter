# Configuration

terraform {
  required_version = ">= 1.14"

  backend "gcs" {
    bucket = "ms-tfstate-c1984596bdabf023"
    prefix = "projects/counter/baseline/apps/web/foundation" # 🎨 TEMPLATE EJECT: Update the prefix (!)
  }

}

# Module imports

module "common" {
  source = "../../../infra/common"
}
