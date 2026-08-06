resource "aws_cloudwatch_log_group" "lambda" {
  name              = "/aws/lambda/${var.project}-python"
  retention_in_days = var.log_retention_days
}

resource "aws_lambda_function" "python_sandbox" {
  function_name = "${var.project}-python"
  role          = aws_iam_role.lambda_sandbox.arn

  package_type = "Image"
  image_uri    = "${aws_ecr_repository.sandbox.repository_url}:${var.lambda_image_tag}"

  memory_size = 512 # ~0.3 vCPU at this tier — see ../../README notes on the CPU-core caveat
  timeout     = 30

  # Lambda's ephemeral storage floor is 512MB; it cannot be configured down to the spec's
  # "100MB disk" figure. That limit is enforced by the workload itself (a <=1MB CSV, nothing
  # else meaningfully writes to /tmp), not by this platform setting.
  ephemeral_storage {
    size = 512
  }

  vpc_config {
    subnet_ids         = aws_subnet.sandbox_private[*].id
    security_group_ids = [aws_security_group.lambda_sandbox.id]
  }

  environment {
    variables = {
      DATASET_BUCKET = aws_s3_bucket.datasets.bucket
    }
  }

  # No reserved_concurrent_executions here: this account's total Lambda concurrency ceiling is
  # only 10 (aws lambda get-account-settings), and AWS requires >=10 stay unreserved at all
  # times — there's no room to reserve any amount without violating that floor. The account-wide
  # ceiling itself already caps total concurrency tightly, just not per-function. Request a
  # service quota increase first if per-function reservation becomes necessary later.

  depends_on = [
    aws_iam_role_policy.s3_read_only,
    aws_iam_role_policy.logs,
    aws_iam_role_policy_attachment.vpc_access,
    aws_cloudwatch_log_group.lambda,
  ]
}
