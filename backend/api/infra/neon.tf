# Neon (serverless Postgres) project backing the counter store.
resource "neon_project" "main" {
  name      = "${module.common.project_base_name}-${module.common.project_variant}"
  region_id = "aws-eu-central-1"
}

# JDBC connection string for the default branch/database/role. We use the direct
# (non-pooled) endpoint so Flyway's session-level advisory lock works reliably at
# startup; PgBouncer's transaction pooling would make that lock unreliable. The
# per-instance Hikari pool is small and Cloud Run runs few instances, so direct
# connections are well within Neon's limits for this workload. Prefixing the
# Postgres URI with `jdbc:` yields a URL the PostgreSQL JDBC driver accepts
# (credentials live in the userinfo component).
locals {
  database_jdbc_url = "jdbc:${neon_project.main.connection_uri}"
}
