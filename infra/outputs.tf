output "app_url" {
  description = "The HTTPS URL to send your friends."
  value       = "https://${aws_apprunner_service.main.service_url}"
}

output "google_redirect_uri" {
  description = "Paste this into Google Cloud Console as an Authorised redirect URI."
  value       = "https://${aws_apprunner_service.main.service_url}/login/oauth2/code/google"
}

output "plaid_webhook_url" {
  description = "Optional: set this as PLAID_WEBHOOK_URL to receive transaction updates."
  value       = "https://${aws_apprunner_service.main.service_url}/api/webhook"
}

output "ecr_repository_url" {
  description = "Push the Docker image here, then re-apply with a new image_tag."
  value       = aws_ecr_repository.app.repository_url
}

output "database_endpoint" {
  description = "RDS endpoint (private to the VPC)."
  value       = aws_db_instance.main.endpoint
}

output "secret_arn" {
  description = "Secrets Manager entry holding the runtime secrets."
  value       = aws_secretsmanager_secret.app.arn
}
