#!/usr/bin/env bash
# Run on EC2-2 and EC2-3. Joins the k3s cluster as a worker.
# Requires env vars K3S_URL, K3S_TOKEN, NODE_NAME (set by the server script).
set -euo pipefail

: "${K3S_URL:?K3S_URL not set — copy the command printed by bootstrap-k3s-server.sh}"
: "${K3S_TOKEN:?K3S_TOKEN not set}"
: "${NODE_NAME:?NODE_NAME not set (ec2-2 or ec2-3)}"

echo "==> Disabling swap"
sudo swapoff -a
sudo sed -i.bak '/ swap / s/^/#/' /etc/fstab

echo "==> Installing k3s agent and joining $K3S_URL as $NODE_NAME"
curl -sfL https://get.k3s.io | \
  K3S_URL="$K3S_URL" K3S_TOKEN="$K3S_TOKEN" \
  INSTALL_K3S_EXEC="agent --node-name=$NODE_NAME" \
  sh -

echo
echo "==> Installing Docker (so we can build/import images locally)"
sudo apt-get update -y
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
  | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
  https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list
sudo apt-get update -y
sudo apt-get install -y docker-ce docker-ce-cli docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker "$USER"

echo
echo "==> Done. From EC2-1 run 'kubectl get nodes' — you should see this node."
