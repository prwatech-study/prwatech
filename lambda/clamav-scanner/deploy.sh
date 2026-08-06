#!/usr/bin/env bash
set -euo pipefail

# Builds and pushes the clamav-scanner Lambda image. Run once before the first `terraform apply`
# that creates this function (see ../practical-sandbox/terraform/main.tf's apply-order comment
# — the same two-step dance applies here), and again after any handler.py/Dockerfile change.
#
# Usage: ./deploy.sh [tag]   (defaults to "latest")

AWS_REGION="${AWS_REGION:-ap-south-1}"
REPO_NAME="skillama-practical-sandbox-clamav-scanner"
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
echo "  cd ../practical-sandbox/terraform && terraform apply -var clamav_scanner_image_tag=${TAG}"
