# Neon (serverless Postgres) project backing the counter store.
resource "neon_project" "main" {
  name      = "${module.common.project_base_name}-${module.common.project_variant}"
  region_id = "aws-eu-central-1"

  # The provider defaults this to 86400s (24h), which exceeds the Free plan's
  # maximum of 21600s (6h). Point-in-time restore isn't needed for a counter, so
  # pin it to the plan maximum to keep the project provisionable on the Free tier.
  history_retention_seconds = 21600
}

# JDBC connection string for the default branch/database/role. We use the direct
# (non-pooled) endpoint so Flyway's session-level advisory lock works reliably at
# startup; PgBouncer's transaction pooling would make that lock unreliable. The
# per-instance Hikari pool is small and Cloud Run runs few instances, so direct
# connections are well within Neon's limits for this workload.
#
# Neon returns a `postgres://…` URI, but the PostgreSQL JDBC driver only registers
# for the `jdbc:postgresql:` scheme (a bare `jdbc:postgres:` fails with "No suitable
# driver"). Normalize the scheme to `postgresql` and prefix `jdbc:`; credentials
# live in the userinfo component, which the driver accepts.
locals {
  database_jdbc_url = "jdbc:${replace(neon_project.main.connection_uri, "/^postgres(ql)?:/", "postgresql:")}"
}
