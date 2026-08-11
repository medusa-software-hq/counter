# Actions variables consumed by CI/CD jobs.
#
# Environment-scoped: prod and staging each get their own API URL. A workflow
# job's `environment:` is what makes its `vars.*` resolve to the right
# environment's values; each Terraform workspace writes only its own environment
# (the prod workspace fills `production`, the staging workspace fills `staging`).
#
# CLOUDFLARE_ZONE_ID is absent here on purpose: the Cloudflare zone is shared
# across environments, so both read the same repository-level value (set outside
# this root).

locals {
  # Everything the CI/CD jobs read, per environment.
  cicd_environment_variables = {
    API_URL = module.common.api_url
    # Client id of the "Medusa Counter Releaser" GitHub App the Publish CLI
    # workflow authenticates as (public; the private key is a secret).
    GH_RELEASES_CLIENT_ID = module.common.gh_releases_client_id
  }
}

resource "github_actions_environment_variable" "cicd" {
  for_each = local.cicd_environment_variables

  repository    = data.github_repository.this.name
  environment   = module.common.gh_environment_name
  variable_name = each.key
  value         = each.value
}
