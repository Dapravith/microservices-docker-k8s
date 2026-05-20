#!/usr/bin/env bash
# Run ONCE on EC2-1 control-plane.
# Initializes Kubernetes and prints the join command for EC2-2 and EC2-3.
set -euo pipefail

if ! command -v kubeadm >/dev/null 2>&1; then
  echo "ERROR: kubeadm is not installed."
  exit 1
fi

if [[ "$(uname -s)" != "Linux" ]]; then
  echo "ERROR: This script must run on Linux EC2."
  exit 1
fi

POD_CIDR="${POD_CIDR:-10.244.0.0/16}"
CONTROL_PLANE_IP="${CONTROL_PLANE_IP:-$(ip route get 1.1.1.1 | awk '{for(i=1;i<=NF;i++) if($i=="src") {print $(i+1); exit}}')}"
IGNORE_PREFLIGHT_ERRORS="${IGNORE_PREFLIGHT_ERRORS:-Mem}"

if [[ -z "${CONTROL_PLANE_IP}" ]]; then
  echo "ERROR: could not detect the node IP. Set CONTROL_PLANE_IP manually:"
  echo "  CONTROL_PLANE_IP=<private-ip> bash init-control-plane.sh"
  exit 1
fi

if [[ -f /etc/kubernetes/admin.conf ]]; then
  echo "Kubernetes control-plane already initialized."
  echo "Fixing kubeconfig for current user..."

  mkdir -p "$HOME/.kube"
  sudo cp -f /etc/kubernetes/admin.conf "$HOME/.kube/config"
  sudo chown "$(id -u):$(id -g)" "$HOME/.kube/config"
  chmod 600 "$HOME/.kube/config"

  kubectl get nodes -o wide

  echo
  echo "=== Worker join command for EC2-2 and EC2-3 ==="
  echo "$(sudo kubeadm token create --print-join-command) --ignore-preflight-errors=${IGNORE_PREFLIGHT_ERRORS}"
  echo "==============================================="
  exit 0
fi

echo "Using control-plane IP: ${CONTROL_PLANE_IP}"
echo "Using pod CIDR: ${POD_CIDR}"
echo "Ignoring preflight errors: ${IGNORE_PREFLIGHT_ERRORS}"

sudo kubeadm init \
  --pod-network-cidr="${POD_CIDR}" \
  --apiserver-advertise-address="${CONTROL_PLANE_IP}" \
  --cri-socket unix:///run/containerd/containerd.sock \
  --ignore-preflight-errors="${IGNORE_PREFLIGHT_ERRORS}"

mkdir -p "$HOME/.kube"
sudo cp -f /etc/kubernetes/admin.conf "$HOME/.kube/config"
sudo chown "$(id -u):$(id -g)" "$HOME/.kube/config"
chmod 600 "$HOME/.kube/config"

echo "Installing Flannel CNI..."
kubectl apply -f https://github.com/flannel-io/flannel/releases/latest/download/kube-flannel.yml

echo
echo "Waiting for node status..."
sleep 20
kubectl get nodes -o wide

echo
echo "=== Worker join command for EC2-2 and EC2-3 ==="
echo "$(sudo kubeadm token create --print-join-command) --ignore-preflight-errors=${IGNORE_PREFLIGHT_ERRORS}"
echo "==============================================="