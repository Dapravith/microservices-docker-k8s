#!/usr/bin/env bash
set -euo pipefail

TAG="${TAG:-1.0.0}"
IMAGE_REPO="${IMAGE_REPO:-dapravith99}"

for service in api-gateway login-service student-service teacher-service; do
  docker push "$IMAGE_REPO/$service:$TAG"
done
