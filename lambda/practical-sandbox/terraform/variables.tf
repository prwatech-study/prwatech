variable "aws_region" {
  description = "AWS region for the sandbox infrastructure. Must match the region prwatech's other S3 buckets use (aws.s3.region in application.properties)."
  type        = string
  default     = "ap-south-1"
}

variable "project" {
  description = "Name prefix applied to every resource this module creates."
  type        = string
  default     = "skillama-practical-sandbox"
}

variable "dataset_bucket_name" {
  description = "S3 bucket for practical-exercise CSV datasets. Must match aws.s3.practical-datasets-bucket-name in prwatech's application.properties."
  type        = string
  default     = "skillama-practical-datasets"
}

variable "dataset_retention_days" {
  description = "How long a dataset object lives before the S3 lifecycle rule expires it. Must match PracticalDatasetService.RETENTION_DAYS (currently 90) — keep the two in sync if either changes."
  type        = number
  default     = 90
}

variable "lambda_image_tag" {
  description = "Tag of the image already pushed to the ECR repo this module creates (run deploy.sh first). Terraform cannot create a Lambda function referencing a tag that doesn't exist yet."
  type        = string
  default     = "latest"
}

variable "log_retention_days" {
  description = "CloudWatch Logs retention for the sandbox function's log group."
  type        = number
  default     = 30
}

variable "reserved_concurrent_executions" {
  description = "Caps how many sandbox invocations can run at once, across every course sharing this function — the blast-radius limit if one course's traffic spikes."
  type        = number
  default     = 20
}

# --- ClamAV malware scanning ---

variable "clamav_db_bucket_name" {
  description = "S3 bucket the clamav-updater function publishes virus definitions to and clamav-scanner reads them from."
  type        = string
  default     = "skillama-clamav-db"
}

variable "clamav_scanner_image_tag" {
  description = "Tag pushed to the clamav-scanner ECR repo (run lambda/clamav-scanner/deploy.sh first)."
  type        = string
  default     = "latest"
}

variable "clamav_updater_image_tag" {
  description = "Tag pushed to the clamav-updater ECR repo (run lambda/clamav-updater/deploy.sh first)."
  type        = string
  default     = "latest"
}

variable "clamav_refresh_schedule" {
  description = "How often the clamav-updater function runs. ClamAV publishes daily.cvd updates multiple times a day; this doesn't need to be more frequent than that."
  type        = string
  default     = "rate(12 hours)"
}

variable "prwatech_invoker_principal_arn" {
  description = "IAM role/user ARN the prwatech backend runs as — the only principal allowed to invoke clamav-scanner. Defaulted to AI-Tutor-Role, the EC2 instance-profile role found attached to all 5 running instances in this account (mongo-db, ai-tutor-dev, skillama-prod, ai-tutor-instance-prod, all-lms-prod) — it's a shared role, not one scoped to prwatech specifically, so granting it invoke access means any of those five instances can call the scanner. Override if that's not acceptable and prwatech should get its own dedicated role instead."
  type        = string
  default     = "arn:aws:iam::733477849411:role/AI-Tutor-Role"
}
