# Container registry for the API image. One repository, shared across
# environments (images are tagged per environment + commit).
resource "aws_ecr_repository" "api" {
  name = "${local.name_prefix}-api"

  image_scanning_configuration {
    scan_on_push = true
  }
}
