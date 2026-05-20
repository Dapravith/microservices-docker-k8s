#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
NODE_ROLE="${NODE_ROLE:?Set NODE_ROLE=admin, student, or teacher}"
TAG="${TAG:-1.0.0}"
IMAGE_REPO="${IMAGE_REPO:-dapravith99}"

cd "$ROOT_DIR"

build_and_import() {
  local service="$1"
  local image="$IMAGE_REPO/$service:$TAG"

  docker build -f "services/$service/Dockerfile" -t "$image" .
  docker save "$image" | sudo k3s ctr images import -
}

case "$NODE_ROLE" in
  admin)
    build_and_import api-gateway
    build_and_import login-service
    ;;
  student)
    build_and_import student-service
    ;;
  teacher)
    build_and_import teacher-service
    ;;
  *)
    echo "Unknown NODE_ROLE '$NODE_ROLE'. Use admin, student, or teacher." >&2
    exit 1
    ;;
esac

sudo k3s ctr images ls | grep "$IMAGE_REPO/"
