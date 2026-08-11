output "tfstate_bucket_name" {
  value = aws_s3_bucket.tfstate.id
}

output "cicd_role_arn" {
  value = aws_iam_role.cicd.arn
}

output "github_oidc_provider_arn" {
  value = aws_iam_openid_connect_provider.github.arn
}

output "ecr_api_repository_url" {
  value = aws_ecr_repository.api.repository_url
}
