#!/usr/bin/env bash
# Single-script Kubernetes apply for the msp stack.
# Run from the control-plane node after `kubeadm init` and worker joins.
#
# Usage:
#   ./apply-k8s.sh                                    # auto-detects nodes
#   EC2_1=ip-... EC2_2=ip-... EC2_3=ip-... ./apply-k8s.sh
#   DOCKERHUB_USER=youruser ./apply-k8s.sh

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
K8S_DIR="${ROOT}/k8s"
NS="msp"
DOCKERHUB_USER="${DOCKERHUB_USER:-dapravith99}"

echo "==> Pre-flight"
command -v kubectl >/dev/null || { echo "ERROR: kubectl not on PATH"; exit 1; }
kubectl cluster-info >/dev/null 2>&1 || { echo "ERROR: kubectl can't reach the cluster (~/.kube/config?)"; exit 1; }

# Resolve node names (env override > auto-detect)
EC2_1="${EC2_1:-$(kubectl get nodes -l node-role.kubernetes.io/control-plane -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || true)}"
mapfile -t WORKERS < <(kubectl get nodes -l '!node-role.kubernetes.io/control-plane' -o jsonpath='{range .items[*]}{.metadata.name}{"\n"}{end}' 2>/dev/null || true)
EC2_2="${EC2_2:-${WORKERS[0]:-}}"
EC2_3="${EC2_3:-${WORKERS[1]:-}}"

NODE_COUNT=$(kubectl get nodes --no-headers 2>/dev/null | wc -l | tr -d ' ')
echo "    Nodes detected: ${NODE_COUNT}"
echo "    EC2_1 (frontend+mongo): ${EC2_1:-<missing>}"
echo "    EC2_2 (student):        ${EC2_2:-<missing>}"
echo "    EC2_3 (teacher):        ${EC2_3:-<missing>}"
echo

# ----------------------------------------------------------------------------
# 1. Label nodes per topology (label-nodes.sh logic, inlined)
# ----------------------------------------------------------------------------
echo "==> Labeling nodes"
if [[ -n "${EC2_1}" ]]; then
  kubectl label --overwrite node "${EC2_1}" role=frontend db=mongo
  # If single-node or you want frontend pods on control-plane: remove the taint
  kubectl taint node "${EC2_1}" node-role.kubernetes.io/control-plane- 2>/dev/null || true
fi
[[ -n "${EC2_2}" ]] && kubectl label --overwrite node "${EC2_2}" role=student
[[ -n "${EC2_3}" ]] && kubectl label --overwrite node "${EC2_3}" role=teacher
echo

# ----------------------------------------------------------------------------
# 2. Mongo hostPath directory on the db=mongo node (only if we're that node)
# ----------------------------------------------------------------------------
SELF="$(hostname)"
if [[ "${SELF}" == "${EC2_1}" ]]; then
  sudo mkdir -p /var/lib/mongo-data
  sudo chmod 777 /var/lib/mongo-data
fi

# ----------------------------------------------------------------------------
# 3. Namespace, secrets, mongo (delete prior state so fresh PV binds cleanly)
# ----------------------------------------------------------------------------
echo "==> Applying namespace + secrets"
kubectl apply -f "${K8S_DIR}/00-namespace.yaml"
kubectl apply -f "${K8S_DIR}/01-secrets.yaml"
echo

echo "==> Resetting mongo state (PV/PVC/StatefulSet)"
kubectl delete statefulset mongodb -n "${NS}" --ignore-not-found
kubectl delete pvc --all -n "${NS}" --ignore-not-found
kubectl delete pv mongo-pv --ignore-not-found
echo

echo "==> Applying mongo"
kubectl apply -f "${K8S_DIR}/mongodb.yaml"
kubectl apply -f "${K8S_DIR}/mongodb-service.yaml"   # idempotent: duplicate of Service in mongodb.yaml
echo

# ----------------------------------------------------------------------------
# 4. App deployments + services (DOCKERHUB_USER placeholder substituted)
# ----------------------------------------------------------------------------
echo "==> Applying app services (DOCKERHUB_USER=${DOCKERHUB_USER})"
for svc in auth registration student teacher api-gateway; do
  sed "s|DOCKERHUB_USER|${DOCKERHUB_USER}|g" "${K8S_DIR}/${svc}.yaml" | kubectl apply -f -
  kubectl apply -f "${K8S_DIR}/${svc}-service.yaml"
done
echo

# ----------------------------------------------------------------------------
# 5. Single-node fallback: strip role-pinning nodeAffinity
# ----------------------------------------------------------------------------
if [ "${NODE_COUNT}" -lt 2 ]; then
  echo "==> Single-node mode: stripping nodeAffinity so all app pods can schedule"
  for d in authentication-service registration student-service teacher-service api-gateway; do
    kubectl -n "${NS}" patch deploy "$d" --type=json \
      -p='[{"op":"remove","path":"/spec/template/spec/affinity"}]' 2>/dev/null || true
  done
  echo
fi

# ----------------------------------------------------------------------------
# 6. Wait for rollouts
# ----------------------------------------------------------------------------
echo "==> Waiting for rollouts (180s each)"
kubectl -n "${NS}" rollout status statefulset/mongodb --timeout=180s
for d in authentication-service registration student-service teacher-service api-gateway; do
  kubectl -n "${NS}" rollout status "deploy/${d}" --timeout=180s
done
echo

# ----------------------------------------------------------------------------
# 7. Summary + gateway URL
# ----------------------------------------------------------------------------
echo "==> Pods"
kubectl -n "${NS}" get pods -o wide
echo
echo "==> Services"
kubectl -n "${NS}" get svc
echo
PUBLIC_IP="$(curl -fsS --max-time 3 http://169.254.169.254/latest/meta-data/public-ipv4 2>/dev/null \
  || curl -fsS --max-time 3 ifconfig.me 2>/dev/null \
  || echo "<EC2-1_PUBLIC_IP>")"
echo "Gateway: http://${PUBLIC_IP}:30000"
