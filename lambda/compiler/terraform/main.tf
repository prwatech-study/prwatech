terraform {
  required_version = ">= 1.5"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.aws_region
}

# The practice-sandbox VPC already exists. This stack only adds compiler functions.
data "aws_vpc" "sandbox" {
  filter {
    name   = "tag:Name"
    values = ["skillama-practical-sandbox-vpc"]
  }
}

data "aws_subnets" "sandbox" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.sandbox.id]
  }
}

locals {
  compilers = {
    python = { memory = 2048, timeout = 60, run_timeout = "50", nproc = "256" }
    java   = { memory = 1024, timeout = 30, run_timeout = "15", nproc = "512" }
    native = { memory = 512, timeout = 30, run_timeout = "15", nproc = "128" }
    dotnet = { memory = 2048, timeout = 60, run_timeout = "50", nproc = "512" }
    go     = { memory = 1024, timeout = 45, run_timeout = "35", nproc = "256" }
    scala  = { memory = 1024, timeout = 60, run_timeout = "45", nproc = "512" }
    sql    = { memory = 512, timeout = 30, run_timeout = "15", nproc = "64" }
    spark  = { memory = 3008, timeout = 90, run_timeout = "60", nproc = "512" }
  }
}

resource "aws_ecr_repository" "compiler" {
  for_each             = local.compilers
  name                 = "skillama-compiler-${each.key}"
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_ecr_lifecycle_policy" "compiler" {
  for_each   = aws_ecr_repository.compiler
  repository = each.value.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep the 2 most recent images"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 2
      }
      action = { type = "expire" }
    }]
  })
}

resource "aws_security_group" "compiler" {
  name        = "skillama-practical-sandbox-compiler-sg"
  description = "Compiler Lambdas: no ingress and no egress"
  vpc_id      = data.aws_vpc.sandbox.id

  tags = {
    Name = "skillama-practical-sandbox-compiler-sg"
  }
}

resource "aws_iam_role" "compiler" {
  name = "skillama-practical-sandbox-compiler-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "lambda.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "compiler_vpc" {
  role       = aws_iam_role.compiler.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaVPCAccessExecutionRole"
}

resource "aws_cloudwatch_log_group" "compiler" {
  for_each          = local.compilers
  name              = "/aws/lambda/skillama-compiler-${each.key}"
  retention_in_days = 30
}

resource "aws_iam_role_policy" "compiler_logs" {
  name = "skillama-practical-sandbox-compiler-logs"
  role = aws_iam_role.compiler.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Action = ["logs:CreateLogGroup", "logs:CreateLogStream", "logs:PutLogEvents"]
      Resource = [for group in aws_cloudwatch_log_group.compiler : "${group.arn}:*"]
    }]
  })
}

resource "aws_lambda_function" "compiler" {
  for_each      = local.compilers
  function_name = "skillama-compiler-${each.key}"
  role          = aws_iam_role.compiler.arn
  package_type  = "Image"
  image_uri     = "${aws_ecr_repository.compiler[each.key].repository_url}:${var.compiler_image_tag}"
  memory_size   = each.value.memory
  timeout       = each.value.timeout

  ephemeral_storage {
    size = each.key == "spark" ? 2048 : 512
  }

  vpc_config {
    subnet_ids         = data.aws_subnets.sandbox.ids
    security_group_ids = [aws_security_group.compiler.id]
  }

  environment {
    variables = {
      COMPILER_FAMILY = each.key
      RUN_TIMEOUT_SEC = each.value.run_timeout
      RUN_NPROC       = each.value.nproc
    }
  }

  depends_on = [
    aws_iam_role_policy.compiler_logs,
    aws_iam_role_policy_attachment.compiler_vpc,
    aws_cloudwatch_log_group.compiler,
  ]
}

resource "aws_lambda_permission" "compiler_invoke" {
  for_each      = local.compilers
  statement_id  = "AllowPrwatechInvoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.compiler[each.key].function_name
  principal     = var.prwatech_invoker_principal_arn
}
