# Practical-exercise CSV sandbox — infrastructure only, nothing here executes learner code.
# See ../handler.py for the sandbox itself and the design proposal for the full architecture.
#
# Apply order (Lambda's package_type=Image requires an image to already exist in ECR, but this
# same module creates all three ECR repos — so the very first apply is two rounds):
#   1. terraform apply -target=aws_ecr_repository.sandbox \
#                       -target=aws_ecr_repository.clamav_scanner \
#                       -target=aws_ecr_repository.clamav_updater
#   2. ../deploy.sh && ../clamav-scanner/deploy.sh && ../clamav-updater/deploy.sh
#   3. terraform apply   # creates the VPC, buckets, IAM roles, and all three functions
#   4. Invoke clamav-updater once by hand (see clamav-updater/deploy.sh's printed command) to
#      populate the DB bucket — clamav-scanner will error until that's done at least once.
#   5. Fill in prwatech_invoker_principal_arn and re-apply, so prwatech can actually call
#      clamav-scanner.
# Any later handler.py change just needs steps 2-3 again for the affected function(s) (with a
# new -var ..._image_tag if you want to keep the old one around instead of overwriting "latest").

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

data "aws_caller_identity" "current" {}
