variable "region" {
  description = "AWS region to deploy into."
  type        = string
  default     = "us-west-2"
}

variable "name" {
  description = "Name prefix for every resource."
  type        = string
  default     = "centsight"
}

variable "image_tag" {
  description = "Image tag in ECR to run. Change this and re-apply to deploy a new build."
  type        = string
  default     = "latest"
}

variable "plaid_client_id" {
  description = "Plaid client_id (not secret, but environment-specific)."
  type        = string
}

variable "plaid_secret" {
  description = "Plaid secret for the chosen environment."
  type        = string
  sensitive   = true
}

variable "plaid_env" {
  description = "sandbox or production. Sandbox uses Plaid's fake test banks."
  type        = string
  default     = "sandbox"

  validation {
    condition     = contains(["sandbox", "production"], var.plaid_env)
    error_message = "plaid_env must be sandbox or production."
  }
}

variable "google_client_id" {
  description = "OAuth client id from Google Cloud Console."
  type        = string
}

variable "google_client_secret" {
  description = "OAuth client secret from Google Cloud Console."
  type        = string
  sensitive   = true
}

variable "allowed_emails" {
  description = "Optional comma-separated guest list. Empty means any Google account can sign in."
  type        = string
  default     = ""
}

variable "db_instance_class" {
  description = "RDS instance size. t4g.micro is the cheapest that fits."
  type        = string
  default     = "db.t4g.micro"
}

variable "app_cpu" {
  description = "App Runner vCPU (in App Runner units)."
  type        = string
  default     = "0.25 vCPU"
}

variable "app_memory" {
  description = "App Runner memory."
  type        = string
  default     = "0.5 GB"
}
