# HTTPS certificate

resource "google_compute_managed_ssl_certificate" "primary" {
  name = "web-cert"

  managed {
    domains = [local.counter_web_host_name]
  }
}

# HTTPS URL map

resource "google_compute_url_map" "primary" {
  name            = "primary-url-map"
  default_service = google_compute_backend_service.primary_service_compute_backend.id

  host_rule {
    hosts        = [local.counter_web_host_name]
    path_matcher = "paths"
  }

  path_matcher {
    name            = "paths"
    default_service = google_compute_backend_service.primary_service_compute_backend.id
  }
}

# Load Balancer IP

resource "google_compute_global_address" "alb_ip" {
  name = "primary-alb-ip"
}

# HTTPS proxy + forwarding rule

resource "google_compute_target_https_proxy" "primary" {
  name             = "web-https-proxy"
  url_map          = google_compute_url_map.primary.id
  ssl_certificates = [google_compute_managed_ssl_certificate.primary.id]
}

resource "google_compute_global_forwarding_rule" "https" {
  name                  = "web-https-forwarding-rule"
  load_balancing_scheme = "EXTERNAL_MANAGED"
  target                = google_compute_target_https_proxy.primary.id
  ip_address            = google_compute_global_address.alb_ip.id
  port_range            = "443"
}

# Outputs

output "load_balancer_ip" {
  description = "Static IP address of the Application Load Balancer."
  value       = google_compute_global_address.alb_ip.address
}

# Imports

import {
  to = google_compute_managed_ssl_certificate.primary
  id = "projects/ms-counter-1c326d3a/global/sslCertificates/web-cert"
}

import {
  to = google_compute_global_address.alb_ip
  id = "projects/ms-counter-1c326d3a/global/addresses/primary-alb-ip"
}

import {
  to = google_compute_region_network_endpoint_group.primary_service_neg
  id = "projects/ms-counter-1c326d3a/regions/europe-west1/networkEndpointGroups/primary-neg"
}
