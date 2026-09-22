#!/usr/bin/env bash
# Build the image, push it to ECR, and roll App Runner onto it.
# Usage: ./deploy.sh [tag]      (default tag: current git sha)
set -euo pipefail

cd "$(dirname "$0")"

TAG="${1:-$(git rev-parse --short HEAD 2>/dev/null || date +%s)}"
REPO=$(tofu output -raw ecr_repository_url 2>/dev/null || terraform output -raw ecr_repository_url)
REGION=$(echo "$REPO" | cut -d. -f4)
REGISTRY=$(echo "$REPO" | cut -d/ -f1)

echo "==> Logging in to ECR ($REGION)"
aws ecr get-login-password --region "$REGION" | docker login --username AWS --password-stdin "$REGISTRY"

echo "==> Building $REPO:$TAG (linux/amd64 for App Runner)"
docker build --platform linux/amd64 -t "$REPO:$TAG" ..

echo "==> Pushing"
docker push "$REPO:$TAG"

echo "==> Pointing App Runner at $TAG"
(tofu apply -var "image_tag=$TAG" -auto-approve 2>/dev/null) || terraform apply -var "image_tag=$TAG" -auto-approve

echo
echo "Deployed. Your app:"
(tofu output -raw app_url 2>/dev/null || terraform output -raw app_url)
echo
