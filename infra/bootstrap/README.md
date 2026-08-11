# Bootstrap

The account-wide AWS foundation every other Terraform root depends on: the S3
state bucket, the GitHub Actions OIDC provider, the CI/CD deploy role, and the
API's ECR repository. Applied by an admin, with AWS credentials in the
environment. If the AWS CLI is authenticated but Terraform reports
`No valid credential sources found`, bridge the CLI profile into the
environment first: `eval "$(aws configure export-credentials --format env)"`.

It provisions the very bucket every root — including, after the first apply,
itself — uses as its backend, so a brand-new bootstrap is a two-step first
apply:

```bash
terraform init -backend=false   # local state; the bucket does not exist yet
terraform apply                 # creates the bucket + the rest
terraform init -migrate-state   # move bootstrap state into the new bucket
```

Thereafter it is a normal root (`terraform init && terraform apply`), its state
in `s3://<bucket>/bootstrap`.

If the account already has a GitHub Actions OIDC provider, import it first:

```bash
terraform import aws_iam_openid_connect_provider.github \
  arn:aws:iam::682544514886:oidc-provider/token.actions.githubusercontent.com
```
