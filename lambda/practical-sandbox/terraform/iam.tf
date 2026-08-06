resource "aws_iam_role" "lambda_sandbox" {
  name = "${var.project}-lambda-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "lambda.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

# ENI create/describe/delete — required for any Lambda function attached to a VPC, regardless
# of what the function itself does.
resource "aws_iam_role_policy_attachment" "vpc_access" {
  role       = aws_iam_role.lambda_sandbox.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaVPCAccessExecutionRole"
}

# Read-only, and only the practical-datasets/ prefix of this one bucket — this role has no
# other AWS permission of any kind, so a full sandbox escape still can't reach another course's
# data, another bucket, or any other service.
resource "aws_iam_role_policy" "s3_read_only" {
  name = "${var.project}-s3-read"
  role = aws_iam_role.lambda_sandbox.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["s3:GetObject"]
      Resource = "${aws_s3_bucket.datasets.arn}/practical-datasets/*"
    }]
  })
}

resource "aws_iam_role_policy" "logs" {
  name = "${var.project}-logs"
  role = aws_iam_role.lambda_sandbox.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["logs:CreateLogGroup", "logs:CreateLogStream", "logs:PutLogEvents"]
      Resource = "${aws_cloudwatch_log_group.lambda.arn}:*"
    }]
  })
}
