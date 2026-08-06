#!/usr/bin/env bash
set -euo pipefail

# Builds and pushes the clamav-updater Lambda image. Run once before the first `terraform apply`
# that creates this function, and again after any handler.py/Dockerfile/freshclam.conf change.
#
# Usage: ./deploy.sh [tag]   (defaults to "latest")

AWS_REGION="${AWS_REGION:-ap-south-1}"
REPO_NAME="skillama-practical-sandbox-clamav-updater"
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
echo "  cd ../practical-sandbox/terraform && terraform apply -var clamav_updater_image_tag=${TAG}"
echo
echo "After the first apply, invoke it once manually to populate the DB bucket before the"
echo "scanner function needs it:"
echo "  aws lambda invoke --function-name skillama-practical-sandbox-clamav-updater --cli-binary-format raw-in-base64-out --payload '{}' /tmp/out.json && cat /tmp/out.json"
