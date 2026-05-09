#!/usr/bin/env bash
# Run ONCE on EC2-1 control plane. Prints the join command for workers.
set -euo pipefail

if ! command -v kubeadm >/dev/null 2>&1; then
  echo "ERROR: kubeadm is not installed."
  echo "Run this script on the Linux EC2 control-plane node after installing kubeadm, kubelet, and kubectl."
  exit 1
fi

if [[ "$(uname -s)" != "Linux" ]]; then
  echo "ERROR: This script must run on Linux EC2, not macOS."
  exit 1
fi

POD_CIDR="${POD_CIDR:-10.244.0.0/16}"

CONTROL_PLANE_IP="${CONTROL_PLANE_IP:-$(ip route get 1.1.1.1 | awk '{print $7; exit}')}"

echo "Using control-plane IP: ${CONTROL_PLANE_IP}"
echo "Using pod CIDR: ${POD_CIDR}"

sudo kubeadm init \
  --pod-network-cidr="${POD_CIDR}" \
  --apiserver-advertise-address="${CONTROL_PLANE_IP}"

mkdir -p "$HOME/.kube"
sudo cp -f /etc/kubernetes/admin.conf "$HOME/.kube/config"
sudo chown "$(id -u):$(id -g)" "$HOME/.kube/config"

kubectl apply -f https://raw.githubusercontent.com/flannel-io/flannel/master/Documentation/kube-flannel.yml

echo
echo "=== Worker join command ==="
sudo kubeadm token create --print-join-command
echo "==========================="