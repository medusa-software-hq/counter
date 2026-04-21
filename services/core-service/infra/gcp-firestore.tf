# Firestore database
resource "google_firestore_database" "main" {
  project     = var.gcp_project_id
  name        = "(default)"
  location_id = module.common.gcp_primary_location
  type        = "FIRESTORE_NATIVE"
}

# Grant the Cloud Run service account Firestore access
resource "google_project_iam_member" "primary_service_sa_datastore_user" {
  project = var.gcp_project_id
  role    = "roles/datastore.user"
  member  = "serviceAccount:${google_service_account.primary_service_sa.email}"
}
