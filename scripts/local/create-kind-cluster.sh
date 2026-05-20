#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CLUSTER_NAME="${KIND_CLUSTER_NAME:-aupp-local}"

if kind get clusters | grep -qx "$CLUSTER_NAME"; then
  echo "kind cluster '$CLUSTER_NAME' already exists"
else
  kind create cluster --config "$ROOT_DIR/deploy/k8s/kind-cluster.yaml"
fi

kubectl config use-context "kind-$CLUSTER_NAME" >/dev/null

kubectl label node "$CLUSTER_NAME-control-plane" role=admin --overwrite
kubectl label node "$CLUSTER_NAME-worker" role=student --overwrite
kubectl label node "$CLUSTER_NAME-worker2" role=teacher --overwrite
kubectl taint nodes "$CLUSTER_NAME-control-plane" node-role.kubernetes.io/control-plane- 2>/dev/null || true

echo
kubectl get nodes --show-labels
