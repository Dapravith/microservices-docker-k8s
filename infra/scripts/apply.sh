#!/usr/bin/env bash
# Minimal apply for the msp stack.
# Assumes: kubeadm cluster is up, ~/.kube/config is set, images are pushed to Docker Hub.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
K8S="${ROOT}/k8s"
NS="msp"
DOCKERHUB_USER="${DOCKERHUB_USER:-dapravith99}"

# Single-node prep: allow control-plane scheduling, label for mongo
kubectl taint nodes --all node-role.kubernetes.io/control-plane- 2>/dev/null || true
NODE=$(kubectl get nodes -o jsonpath='{.items[0].metadata.name}')
kubectl label node "$NODE" db=mongo --overwrite

# Reset mongo storage so the new PV/PVC pair matches cleanly
kubectl delete statefulset mongodb -n "$NS" --ignore-not-found
kubectl delete pvc --all -n "$NS" --ignore-not-found
kubectl delete pv mongo-pv --ignore-not-found
sudo mkdir -p /var/lib/mongo-data && sudo chmod 777 /var/lib/mongo-data

# Base + mongo
kubectl apply -f "$K8S/00-namespace.yaml"
kubectl apply -f "$K8S/01-secrets.yaml"
kubectl apply -f "$K8S/mongodb.yaml"
kubectl apply -f "$K8S/mongodb-service.yaml"

# App deployments (DOCKERHUB_USER placeholder swapped on the fly)
for svc in auth registration student teacher api-gateway; do
  sed "s|DOCKERHUB_USER|${DOCKERHUB_USER}|g" "$K8S/${svc}.yaml" | kubectl apply -f -
  kubectl apply -f "$K8S/${svc}-service.yaml"
done

# Single-node: strip role-pinning nodeAffinity so pods can schedule on the lone node
if [ "$(kubectl get nodes --no-headers | wc -l)" -eq 1 ]; then
  for d in authentication-service registration student-service teacher-service api-gateway; do
    kubectl -n "$NS" patch deploy "$d" --type=json \
      -p='[{"op":"remove","path":"/spec/template/spec/affinity"}]' 2>/dev/null || true
  done
fi

# Wait for rollouts
kubectl -n "$NS" rollout status statefulset/mongodb --timeout=180s
for d in authentication-service registration student-service teacher-service api-gateway; do
  kubectl -n "$NS" rollout status deploy/"$d" --timeout=180s
done

echo "Done. Gateway: http://$(curl -s ifconfig.me):30000"
