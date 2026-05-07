#!/usr/bin/env bash
# Build all four service images and push them to a Docker registry.
# Usage: DOCKERHUB_USER=youruser ./infra/scripts/build-and-push.sh
set -euo pipefail

DOCKERHUB_USER="${DOCKERHUB_USER:?set DOCKERHUB_USER (or any registry prefix) before running}"
TAG="${TAG:-1.0}"

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

build_push() {
  local svc="$1"
  local img="$2"
  echo "==> building $svc"
  docker build --platform=linux/amd64 -t "${DOCKERHUB_USER}/${img}:${TAG}" "${ROOT}/${svc}"
  docker push "${DOCKERHUB_USER}/${img}:${TAG}"
}

build_push login-service   ms-login
build_push student-service ms-student
build_push teacher-service ms-teacher
build_push api-gateway     ms-gateway

echo
echo "All four images pushed:"
for img in ms-login ms-student ms-teacher ms-gateway; do
  echo "  ${DOCKERHUB_USER}/${img}:${TAG}"
done
echo
echo "Now substitute DOCKERHUB_USER in k8s/*.yaml — e.g.:"
echo "  sed -i.bak \"s|DOCKERHUB_USER|${DOCKERHUB_USER}|g\" k8s/*.yaml"
