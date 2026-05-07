#!/usr/bin/env bash
# Run ONCE on EC2-1 (control plane). Prints the join command for the workers.
set -euo pipefail

POD_CIDR="${POD_CIDR:-10.244.0.0/16}"

sudo kubeadm init \
  --pod-network-cidr="${POD_CIDR}" \
  --apiserver-advertise-address="$(hostname -I | awk '{print $1}')"

mkdir -p "$HOME/.kube"
sudo cp -f /etc/kubernetes/admin.conf "$HOME/.kube/config"
sudo chown "$(id -u):$(id -g)" "$HOME/.kube/config"

# Flannel CNI — simple and works with the default pod CIDR
kubectl apply -f https://raw.githubusercontent.com/flannel-io/flannel/master/Documentation/kube-flannel.yml

echo
echo "=== Worker join command ==="
sudo kubeadm token create --print-join-command
echo "==========================="
