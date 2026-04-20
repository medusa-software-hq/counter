locals {
  # Prefix for GCP resources
  counter_web_prefix = "counter-web"

  # Domain
  counter_web_subdomain_name = "counter"
  counter_web_host_name      = "${local.counter_web_subdomain_name}.${module.common.organization_domain}"
}
