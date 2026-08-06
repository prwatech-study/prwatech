#!/usr/bin/env bash
set -euo pipefail

# Builds and pushes the practical-sandbox Lambda container image to the ECR repo the Terraform
# in ./terraform creates. Run this once before the first `terraform apply` that creates the
# Lambda function itself (it can't reference an image tag that doesn't exist yet — see
# terraform/main.tf), and again after any change to handler.py or requirements.txt.
#
# Usage: ./deploy.sh [tag]   (defaults to "latest")

AWS_REGION="${AWS_REGION:-ap-south-1}"
REPO_NAME="skillama-practical-sandbox"
TAG="${1:-latest}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"
REGISTRY="${ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
IMAGE="${REGISTRY}/${REPO_NAME}:${TAG}"

echo "Building ${IMAGE}..."
docker build --platform linux/amd64 --provenance=false --sbom=false -t "$IMAGE" "$SCRIPT_DIR"

echo "Logging in to ${REGISTRY}..."
aws ecr get-login-password --region "$AWS_REGION" | docker login --username AWS --password-stdin "$REGISTRY"

echo "Pushing ${IMAGE}..."
docker push "$IMAGE"

echo
echo "Pushed. Now run:"
echo "  cd terraform && terraform apply -var lambda_image_tag=${TAG}"
