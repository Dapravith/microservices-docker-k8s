#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

"$ROOT_DIR/scripts/ec2/label-nodes.sh"

kubectl apply -k "$ROOT_DIR/deploy/k8s"

# pods to pick up freshly rebuilt images: the :1.0.0 tag is fixed and
# imagePullPolicy is IfNotPresent, so `kubectl apply` alone leaves old pods running.
kubectl -n aupp rollout restart \
  deployment/api-gateway \
  deployment/login-service \
  deployment/student-service \
  deployment/teacher-service

kubectl -n aupp rollout status statefulset/mongodb --timeout=300s
kubectl -n aupp rollout status deployment/login-service --timeout=300s
kubectl -n aupp rollout status deployment/teacher-service --timeout=300s
kubectl -n aupp rollout status deployment/student-service --timeout=300s
kubectl -n aupp rollout status deployment/api-gateway --timeout=300s

echo
kubectl -n aupp get deploy,sts,svc,pvc -o wide
echo
kubectl -n aupp get pods -o wide
echo
echo "Gateway NodePort: http://<EC2-1-public-ip>:30080"
