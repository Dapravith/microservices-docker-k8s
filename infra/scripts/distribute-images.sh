#!/usr/bin/env bash
# Build all 4 images on EC2-1, then ship them to EC2-2 and EC2-3 over SSH
# straight into k3s's containerd. No external registry required.
#
# Usage (run from EC2-1):
#   EC2_2_IP=10.0.0.12 EC2_3_IP=10.0.0.13 \
#     bash infra/scripts/distribute-images.sh
#
# Both IPs are the *private* IPs of the worker nodes.
# Assumes you ssh as 'ubuntu' with key forwarding (ssh -A) or your key on disk.
set -euo pipefail

: "${EC2_2_IP:?set EC2_2_IP to the private IP of the student node}"
: "${EC2_3_IP:?set EC2_3_IP to the private IP of the teacher node}"
SSH_USER="${SSH_USER:-ubuntu}"
TAG="${TAG:-1.0.0}"

SERVICES=(api-gateway auth-service student-service teacher-service)

cd "$(dirname "$0")/../.."

echo "==> Building 4 images locally on EC2-1"
for svc in "${SERVICES[@]}"; do
  docker build -t "aupp/${svc}:${TAG}" "./${svc}"
done

echo "==> Loading images into the local k3s containerd"
for svc in "${SERVICES[@]}"; do
  docker save "aupp/${svc}:${TAG}" | sudo k3s ctr images import -
done

echo "==> Shipping images to EC2-2 and EC2-3"
# student-service only needs to land on EC2-2; teacher-service only on EC2-3.
# Gateway and auth run on EC2-1 already.
ship() {
  local host="$1" image="$2"
  echo "   $image  ->  $host"
  docker save "$image" | ssh -o StrictHostKeyChecking=accept-new "${SSH_USER}@${host}" \
    "sudo k3s ctr images import -"
}

ship "$EC2_2_IP" "aupp/student-service:${TAG}"
ship "$EC2_3_IP" "aupp/teacher-service:${TAG}"

echo
echo "==> Done. Verify on each node:"
echo "   ssh ${SSH_USER}@${EC2_2_IP} 'sudo k3s ctr images ls | grep aupp'"
echo "   ssh ${SSH_USER}@${EC2_3_IP} 'sudo k3s ctr images ls | grep aupp'"
