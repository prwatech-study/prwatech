# Malware scanning for admin-uploaded CSVs — two functions:
#   clamav-updater: scheduled, refreshes virus definitions, publishes them to S3. Runs *outside*
#                   any VPC — it genuinely needs internet access to reach ClamAV's CDN, unlike
#                   everything else in this module.
#   clamav-scanner: invoked synchronously by prwatech (MalwareScanService) with the file bytes,
#                   before the CSV is ever written to the dataset bucket. Reuses the sandbox's
#                   private VPC/subnets/S3-gateway-endpoint — it only ever needs to read the
#                   virus-DB bucket, the same zero-internet-egress posture as the code sandbox.

resource "aws_s3_bucket" "clamav_db" {
  bucket = var.clamav_db_bucket_name

  tags = {
    Name = "${var.project}-clamav-db"
  }
}

resource "aws_s3_bucket_public_access_block" "clamav_db" {
  bucket = aws_s3_bucket.clamav_db.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# --- ECR ---

resource "aws_ecr_repository" "clamav_scanner" {
  name                 = "${var.project}-clamav-scanner"
  image_tag_mutability = "MUTABLE"
  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_ecr_repository" "clamav_updater" {
  name                 = "${var.project}-clamav-updater"
  image_tag_mutability = "MUTABLE"
  image_scanning_configuration {
    scan_on_push = true
  }
}

# --- clamav-scanner: IAM, network, function ---

resource "aws_iam_role" "clamav_scanner" {
  name = "${var.project}-clamav-scanner-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "lambda.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "clamav_scanner_vpc_access" {
  role       = aws_iam_role.clamav_scanner.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaVPCAccessExecutionRole"
}

# Read-only, and only this bucket — the scanner never touches the dataset bucket at all;
# prwatech passes it file bytes directly, not a dataset reference.
resource "aws_iam_role_policy" "clamav_scanner_s3_read" {
  name = "${var.project}-clamav-scanner-s3-read"
  role = aws_iam_role.clamav_scanner.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["s3:GetObject", "s3:ListBucket"]
      Resource = [aws_s3_bucket.clamav_db.arn, "${aws_s3_bucket.clamav_db.arn}/*"]
    }]
  })
}

resource "aws_cloudwatch_log_group" "clamav_scanner" {
  name              = "/aws/lambda/${var.project}-clamav-scanner"
  retention_in_days = var.log_retention_days
}

resource "aws_iam_role_policy" "clamav_scanner_logs" {
  name = "${var.project}-clamav-scanner-logs"
  role = aws_iam_role.clamav_scanner.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["logs:CreateLogGroup", "logs:CreateLogStream", "logs:PutLogEvents"]
      Resource = "${aws_cloudwatch_log_group.clamav_scanner.arn}:*"
    }]
  })
}

# Same egress-to-S3-only posture as the code sandbox's security group, in the same VPC —
# reusing aws_vpc_endpoint.s3 and the private subnets from network.tf.
resource "aws_security_group" "clamav_scanner" {
  name        = "${var.project}-clamav-scanner-sg"
  description = "clamav-scanner: no ingress, egress restricted to the S3 prefix list only"
  vpc_id      = aws_vpc.sandbox.id

  egress {
    description     = "S3 only, via the gateway endpoint"
    from_port       = 443
    to_port         = 443
    protocol        = "tcp"
    prefix_list_ids = [data.aws_prefix_list.s3.id]
  }

  tags = {
    Name = "${var.project}-clamav-scanner-sg"
  }
}

resource "aws_lambda_function" "clamav_scanner" {
  function_name = "${var.project}-clamav-scanner"
  role          = aws_iam_role.clamav_scanner.arn

  package_type = "Image"
  image_uri    = "${aws_ecr_repository.clamav_scanner.repository_url}:${var.clamav_scanner_image_tag}"

  # clamscan needs real headroom to load the ~110MB signature database into memory each cold
  # start (there's no long-lived clamd daemon here, by design — see clamav-scanner/handler.py).
  # 1024MB/60s still hit "scan timed out" on a real cold invocation in production — more memory
  # buys more CPU/network throughput in Lambda, which speeds up both the S3 download and the
  # engine load, not just the memory ceiling.
  memory_size = 1536
  timeout     = 90

  vpc_config {
    subnet_ids         = aws_subnet.sandbox_private[*].id
    security_group_ids = [aws_security_group.clamav_scanner.id]
  }

  environment {
    variables = {
      CLAMAV_DB_BUCKET = aws_s3_bucket.clamav_db.bucket
    }
  }

  depends_on = [
    aws_iam_role_policy.clamav_scanner_s3_read,
    aws_iam_role_policy.clamav_scanner_logs,
    aws_iam_role_policy_attachment.clamav_scanner_vpc_access,
    aws_cloudwatch_log_group.clamav_scanner,
  ]
}

# Grants prwatech's runtime identity permission to invoke this function. Left inert (no
# statement created) until prwatech_invoker_principal_arn is filled in — see variables.tf.
resource "aws_lambda_permission" "clamav_scanner_invoke" {
  count         = var.prwatech_invoker_principal_arn != "" ? 1 : 0
  statement_id  = "AllowPrwatechInvoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.clamav_scanner.function_name
  principal     = var.prwatech_invoker_principal_arn
}

# --- clamav-updater: IAM, function, schedule ---

resource "aws_iam_role" "clamav_updater" {
  name = "${var.project}-clamav-updater-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "lambda.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy" "clamav_updater_s3_write" {
  name = "${var.project}-clamav-updater-s3-write"
  role = aws_iam_role.clamav_updater.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["s3:PutObject"]
      Resource = "${aws_s3_bucket.clamav_db.arn}/clamav-db/*"
    }]
  })
}

resource "aws_cloudwatch_log_group" "clamav_updater" {
  name              = "/aws/lambda/${var.project}-clamav-updater"
  retention_in_days = var.log_retention_days
}

resource "aws_iam_role_policy" "clamav_updater_logs" {
  name = "${var.project}-clamav-updater-logs"
  role = aws_iam_role.clamav_updater.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["logs:CreateLogGroup", "logs:CreateLogStream", "logs:PutLogEvents"]
      Resource = "${aws_cloudwatch_log_group.clamav_updater.arn}:*"
    }]
  })
}

resource "aws_lambda_function" "clamav_updater" {
  function_name = "${var.project}-clamav-updater"
  role          = aws_iam_role.clamav_updater.arn

  package_type = "Image"
  image_uri    = "${aws_ecr_repository.clamav_updater.repository_url}:${var.clamav_updater_image_tag}"

  # 512MB wasn't enough — freshclam's "Testing database" step loads the downloaded signatures
  # into the scanning engine to verify them, the same memory-hungry work clamav-scanner does at
  # 1024MB. Matching that here.
  memory_size = 1024
  timeout     = 600 # freshclam's first full download can take a few minutes

  # Deliberately no vpc_config — this function needs internet access to reach ClamAV's CDN.

  environment {
    variables = {
      CLAMAV_DB_BUCKET = aws_s3_bucket.clamav_db.bucket
    }
  }

  depends_on = [
    aws_iam_role_policy.clamav_updater_s3_write,
    aws_iam_role_policy.clamav_updater_logs,
    aws_cloudwatch_log_group.clamav_updater,
  ]
}

resource "aws_cloudwatch_event_rule" "clamav_refresh" {
  name                = "${var.project}-clamav-refresh"
  description         = "Triggers clamav-updater to refresh virus definitions"
  schedule_expression = var.clamav_refresh_schedule
}

resource "aws_cloudwatch_event_target" "clamav_refresh" {
  rule = aws_cloudwatch_event_rule.clamav_refresh.name
  arn  = aws_lambda_function.clamav_updater.arn
}

resource "aws_lambda_permission" "clamav_refresh_eventbridge" {
  statement_id  = "AllowEventBridgeInvoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.clamav_updater.function_name
  principal     = "events.amazonaws.com"
  source_arn    = aws_cloudwatch_event_rule.clamav_refresh.arn
}
