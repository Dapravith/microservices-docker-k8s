#!/usr/bin/env bash
set -euo pipefail

: "${K3S_URL:?Set K3S_URL=https://<ec2-1-private-ip>:6443}"
: "${K3S_TOKEN:?Set K3S_TOKEN from ec2-1 /var/lib/rancher/k3s/server/node-token}"

NODE_NAME="${NODE_NAME:-$(hostname)}"

sudo apt-get update
sudo apt-get install -y ca-certificates curl docker.io
sudo systemctl enable --now docker

curl -sfL https://get.k3s.io | sudo K3S_URL="$K3S_URL" K3S_TOKEN="$K3S_TOKEN" INSTALL_K3S_EXEC="agent --node-name $NODE_NAME" sh -
