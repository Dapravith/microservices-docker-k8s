#!/usr/bin/env bash
# Run on the control plane after both workers have joined.
# Labels each node so the Deployments land on the correct EC2.
set -euo pipefail

EC2_1="${EC2_1:-}"
EC2_2="${EC2_2:-}"
EC2_3="${EC2_3:-}"

if [[ -z "$EC2_1" || -z "$EC2_2" || -z "$EC2_3" ]]; then
  echo "Usage: EC2_1=<node-name> EC2_2=<node-name> EC2_3=<node-name> $0"
  echo "Available nodes:"
  kubectl get nodes -o name
  exit 1
fi

# Allow scheduling on the control plane (single small cluster, 3 nodes)
kubectl taint nodes "$EC2_1" node-role.kubernetes.io/control-plane- || true

kubectl label node "$EC2_1" role=frontend --overwrite
kubectl label node "$EC2_2" role=student  --overwrite
kubectl label node "$EC2_3" role=teacher  --overwrite

echo "==> Node labels:"
kubectl get nodes --show-labels
