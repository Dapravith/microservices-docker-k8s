#!/usr/bin/env bash
# Build all four service images and push them to a Docker registry.
#
#   REGISTRY=docker.io/yourdockerhubuser TAG=1.0.0 ./build-and-push.sh
#
# If you'd rather not use a registry, copy images to each EC2 with:
#   docker save aupp/api-gateway:1.0.0 | ssh ec2-2 'sudo ctr -n=k8s.io images import -'
set -euo pipefail

REGISTRY="${REGISTRY:-aupp}"
TAG="${TAG:-1.0.0}"
SERVICES=(api-gateway auth-service student-service teacher-service)

cd "$(dirname "$0")/../.."

for svc in "${SERVICES[@]}"; do
  echo "==> Building $svc"
  docker build -t "${REGISTRY}/${svc}:${TAG}" "./${svc}"
done

if [[ "${PUSH:-true}" == "true" && "${REGISTRY}" != "aupp" ]]; then
  for svc in "${SERVICES[@]}"; do
    echo "==> Pushing ${REGISTRY}/${svc}:${TAG}"
    docker push "${REGISTRY}/${svc}:${TAG}"
  done
fi

echo "==> Done. Images:"
docker images | grep -E "(api-gateway|auth-service|student-service|teacher-service)" || true
