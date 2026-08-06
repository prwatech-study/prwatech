# The dataset bucket. Deliberately has no CloudFront distribution, no static-website config, and
# no public-access allowance anywhere below — every read of an object here goes through the
# Lambda's scoped IAM role or prwatech's PracticalDatasetService, never a direct link.

resource "aws_s3_bucket" "datasets" {
  bucket = var.dataset_bucket_name

  tags = {
    Name = "${var.project}-datasets"
  }
}

resource "aws_s3_bucket_public_access_block" "datasets" {
  bucket = aws_s3_bucket.datasets.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_versioning" "datasets" {
  bucket = aws_s3_bucket.datasets.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "datasets" {
  bucket = aws_s3_bucket.datasets.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "aws:kms"
    }
    bucket_key_enabled = true
  }
}

# Mirrors PracticalDatasetService.RETENTION_DAYS (var.dataset_retention_days) — this is the
# actual enforcement of the 90-day retention requirement; the Mongo expiresAt field just lets
# the app answer "is this expired" without waiting on S3 to catch up.
resource "aws_s3_bucket_lifecycle_configuration" "datasets" {
  bucket = aws_s3_bucket.datasets.id

  rule {
    id     = "expire-practical-datasets"
    status = "Enabled"

    filter {
      prefix = "practical-datasets/"
    }

    expiration {
      days = var.dataset_retention_days
    }

    noncurrent_version_expiration {
      noncurrent_days = var.dataset_retention_days
    }
  }
}
