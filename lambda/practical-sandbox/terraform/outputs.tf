output "ecr_repository_url" {
  value       = aws_ecr_repository.sandbox.repository_url
  description = "Push the image here (../deploy.sh) before the first apply that creates the Lambda function — see main.tf's apply-order comment."
}

output "lambda_function_name" {
  value = aws_lambda_function.python_sandbox.function_name
}

output "lambda_function_arn" {
  value       = aws_lambda_function.python_sandbox.arn
  description = "What prwatech's AWS SDK Lambda client invokes."
}

output "dataset_bucket_name" {
  value = aws_s3_bucket.datasets.bucket
}

output "vpc_id" {
  value = aws_vpc.sandbox.id
}

output "clamav_scanner_ecr_repository_url" {
  value       = aws_ecr_repository.clamav_scanner.repository_url
  description = "Push here via lambda/clamav-scanner/deploy.sh before the first apply that creates this function."
}

output "clamav_updater_ecr_repository_url" {
  value       = aws_ecr_repository.clamav_updater.repository_url
  description = "Push here via lambda/clamav-updater/deploy.sh before the first apply that creates this function."
}

output "clamav_scanner_function_arn" {
  value       = aws_lambda_function.clamav_scanner.arn
  description = "What prwatech's MalwareScanService invokes."
}

output "clamav_db_bucket_name" {
  value = aws_s3_bucket.clamav_db.bucket
}
