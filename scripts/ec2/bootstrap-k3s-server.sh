#!/usr/bin/env bash
set -euo pipefail

NODE_NAME="${NODE_NAME:-ec2-1}"

sudo apt-get update
sudo apt-get install -y ca-certificates curl docker.io
sudo systemctl enable --now docker

curl -sfL https://get.k3s.io | sudo INSTALL_K3S_EXEC="server --node-name $NODE_NAME --write-kubeconfig-mode 644 --disable traefik" sh -

mkdir -p "$HOME/.kube"
sudo cp /etc/rancher/k3s/k3s.yaml "$HOME/.kube/config"
sudo chown "$USER:$USER" "$HOME/.kube/config"

echo
echo "K3S_TOKEN:"
sudo cat /var/lib/rancher/k3s/server/node-token
echo
echo "Use this server URL for agents: https://$(hostname -I | awk '{print $1}'):6443"
