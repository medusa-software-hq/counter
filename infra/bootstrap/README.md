# Bootstrap

The account-wide AWS foundation every other Terraform root depends on: the S3
state bucket, the GitHub Actions OIDC provider, and the CI/CD deploy role.

Applied **once**, by an admin, with **local state** (it provisions the very
bucket the other roots use as their backend, so it cannot use that backend
itself). Local state is git-ignored; the resources are all importable by name if
it is lost.

```bash
terraform init
terraform apply
```

If the account already has a GitHub Actions OIDC provider, import it first:

```bash
terraform import aws_iam_openid_connect_provider.github \
  arn:aws:iam::682544514886:oidc-provider/token.actions.githubusercontent.com
```
