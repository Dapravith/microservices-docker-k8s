#!/usr/bin/env bash
# Build all five service images and push them to a Docker registry.
# Usage: ./infra/scripts/build-and-push.sh
set -euo pipefail

DOCKERHUB_USER="${DOCKERHUB_USER:-dapravith99}"
TAG="${TAG:-1.0}"

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

build_push() {
  local svc="$1"
  local img="$2"
  echo "==> building $svc"
  docker build --platform=linux/amd64 -t "${DOCKERHUB_USER}/${img}:${TAG}" "${ROOT}/${svc}"
  docker push "${DOCKERHUB_USER}/${img}:${TAG}"
}

build_push Registration_Microservice   ms-registration
build_push Authentication_Microservice ms-login
build_push Student_Microservice        ms-student
build_push Teacher_Microservice        ms-teacher
build_push APIGateway_Microservice     ms-gateway

echo
echo "All five images pushed:"
for img in ms-registration ms-login ms-student ms-teacher ms-gateway; do
  echo "  ${DOCKERHUB_USER}/${img}:${TAG}"
done

echo
echo "Now substitute DOCKERHUB_USER in k8s/*.yaml — e.g.:"
echo "  sed -i.bak \"s|DOCKERHUB_USER|${DOCKERHUB_USER}|g\" k8s/*.yaml"