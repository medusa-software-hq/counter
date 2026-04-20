locals {
  ttl_auto = 1 # means "automatic" in Cloudflare
}

variable "cloudflare_zone_id" {
  description = "Cloudflare zone ID for the organization domain."
  type        = string
}

resource "cloudflare_dns_record" "app_dns" {
  zone_id = var.cloudflare_zone_id
  type    = "A"
  name    = local.counter_web_subdomain_name
  content = google_compute_global_address.alb_ip.address
  ttl     = local.ttl_auto

  # Keep proxying off so GCP-managed SSL certificate provisioning (ACME HTTP-01 challenge directly to the IP) works.
  proxied = false
}
