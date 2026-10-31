#!/usr/bin/env bash
# Build and push one compiler-family image, or all of them.
# Usage: ./deploy.sh [family|all] [tag]
# Families: python java native dotnet go scala sql spark
# ECR repos are created by ./terraform (aws_ecr_repository.compiler).
# Apply those targets once before the first push.
set -euo pipefail

AWS_REGION="${AWS_REGION:-ap-south-1}"
TAG="${2:-latest}"
FAMILY="${1:-all}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

FAMILIES=(python java native dotnet go scala sql spark)
if [[ "$FAMILY" != "all" ]]; then
  FAMILIES=("$FAMILY")
fi

fetch_file() {
  local url="$1" dest="$2"
  if [[ -s "$dest" ]]; then
    echo "Using cached $(basename "$dest")"
    return 0
  fi
  echo "Downloading $(basename "$dest")..."
  curl -fL --retry 3 --retry-delay 2 -o "${dest}.partial" "$url"
  mv "${dest}.partial" "$dest"
}

# Large archives are fetched on the host. Pulling them inside an emulated
# linux/amd64 build is far slower than the native network.
prefetch_family() {
  local name="$1"
  case "$name" in
    spark)
      local dest="${SCRIPT_DIR}/spark/vendor"
      mkdir -p "$dest"
      fetch_file "https://corretto.aws/downloads/latest/amazon-corretto-17-x64-linux-jdk.tar.gz" "${dest}/corretto17.tgz"
      fetch_file "https://archive.apache.org/dist/spark/spark-3.5.9/spark-3.5.9-bin-hadoop3.tgz" "${dest}/spark35.tgz"
      fetch_file "https://archive.apache.org/dist/spark/spark-4.2.1/spark-4.2.1-bin-hadoop3.tgz" "${dest}/spark42.tgz" \
        || fetch_file "https://archive.apache.org/dist/spark/spark-4.2.1/spark-4.2.1-bin-hadoop3-scala2.13.tgz" "${dest}/spark42.tgz"
      fetch_file "https://github.com/scala/scala/releases/download/v2.13.16/scala-2.13.16.tgz" "${dest}/scala213.tgz" \
        || fetch_file "https://github.com/scala/scala/releases/download/v2.13.15/scala-2.13.15.tgz" "${dest}/scala213.tgz"
      ;;
    dotnet)
      local dest="${SCRIPT_DIR}/dotnet/vendor"
      mkdir -p "$dest"
      local sdk8 sdk10
      sdk8="$(curl -fsSL https://dotnetcli.azureedge.net/dotnet/release-metadata/8.0/releases.json | python3 -c 'import json,sys; print(json.load(sys.stdin)["latest-sdk"])')"
      sdk10="$(curl -fsSL https://dotnetcli.azureedge.net/dotnet/release-metadata/10.0/releases.json | python3 -c 'import json,sys; print(json.load(sys.stdin)["latest-sdk"])')"
      fetch_file "https://builds.dotnet.microsoft.com/dotnet/Sdk/${sdk8}/dotnet-sdk-${sdk8}-linux-x64.tar.gz" "${dest}/dotnet-sdk-8-linux-x64.tar.gz"
      fetch_file "https://builds.dotnet.microsoft.com/dotnet/Sdk/${sdk10}/dotnet-sdk-${sdk10}-linux-x64.tar.gz" "${dest}/dotnet-sdk-10-linux-x64.tar.gz"
      ;;
  esac
}

ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"
REGISTRY="${ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
aws ecr get-login-password --region "$AWS_REGION" | docker login --username AWS --password-stdin "$REGISTRY"

for name in "${FAMILIES[@]}"; do
  REPO="skillama-compiler-${name}"
  if ! aws ecr describe-repositories --region "$AWS_REGION" --repository-names "$REPO" >/dev/null 2>&1; then
    echo "ECR repo ${REPO} does not exist. From practical-sandbox/terraform run:" >&2
    echo "  cd $(dirname "$SCRIPT_DIR")/compiler/terraform && terraform apply -target=aws_ecr_repository.compiler" >&2
    exit 1
  fi
  prefetch_family "$name"
  IMAGE="${REGISTRY}/${REPO}:${TAG}"
  echo "Building ${IMAGE}..."
  # Context is the family directory so multi-hundred-MB vendor archives for
  # other families are not sent to the daemon. handler.py lives one level up.
  cp "${SCRIPT_DIR}/handler.py" "${SCRIPT_DIR}/${name}/handler.py"
  docker build --platform linux/amd64 --provenance=false --sbom=false \
    -f "${SCRIPT_DIR}/${name}/Dockerfile" \
    -t "$IMAGE" \
    "${SCRIPT_DIR}/${name}"
  rm -f "${SCRIPT_DIR}/${name}/handler.py"
  echo "Pushing ${IMAGE}..."
  docker push "$IMAGE"
done

echo
echo "Pushed. Create or update the functions with:"
echo "  cd terraform && terraform apply -var compiler_image_tag=${TAG}"
