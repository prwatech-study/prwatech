# Grants the prwatech application's OWN runtime identity (not any Lambda execution role — those
# are separate and already scoped in iam.tf/clamav.tf) permission to read/write the datasets
# bucket. prwatech's FileStorageServiceImpl calls S3 directly via the AWS SDK's
# DefaultCredentialsProvider, which resolves to whatever IAM role its EC2 instance runs as —
# currently AI-Tutor-Role, the same shared role referenced elsewhere in this module. Missed
# this originally: creating the bucket in s3.tf didn't grant the application any access to it.
#
# Scoped to exactly what PracticalDatasetService/FileStorageServiceImpl actually do: put on
# upload, get on download. No delete — dataset removal is a Mongo soft-delete; the S3 object is
# left for the lifecycle rule in s3.tf to expire, never deleted by the app itself.
resource "aws_iam_role_policy" "prwatech_datasets_access" {
  name = "${var.project}-prwatech-datasets-access"
  role = "AI-Tutor-Role"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["s3:PutObject", "s3:GetObject"]
      Resource = "${aws_s3_bucket.datasets.arn}/practical-datasets/*"
    }]
  })
}
