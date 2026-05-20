#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TAG="${TAG:-1.0.0}"
IMAGE_REPO="${IMAGE_REPO:-dapravith99}"

cd "$ROOT_DIR"

docker build -f services/api-gateway/Dockerfile -t "$IMAGE_REPO/api-gateway:$TAG" .
docker build -f services/login-service/Dockerfile -t "$IMAGE_REPO/login-service:$TAG" .
docker build -f services/student-service/Dockerfile -t "$IMAGE_REPO/student-service:$TAG" .
docker build -f services/teacher-service/Dockerfile -t "$IMAGE_REPO/teacher-service:$TAG" .

docker images "$IMAGE_REPO/*:$TAG"
