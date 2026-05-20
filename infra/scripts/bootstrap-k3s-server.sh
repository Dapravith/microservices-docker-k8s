#!/usr/bin/env bash
# Run on EC2-1 (control plane). Installs k3s, prints the join token + URL.
# k3s is a lightweight Kubernetes distribution — uses ~200 MB instead of
# kubeadm's ~700 MB, which is what makes this fit on a t3.micro (1 GiB).
set -euo pipefail

echo "==> Disabling swap"
sudo swapoff -a
sudo sed -i.bak '/ swap / s/^/#/' /etc/fstab

echo "==> Installing k3s server (no Traefik, we use the gateway pod)"
# --disable=traefik   : we don't need an ingress controller
# --write-kubeconfig-mode=644 : let our user read the kubeconfig
curl -sfL https://get.k3s.io | \
  INSTALL_K3S_EXEC="server --disable=traefik --write-kubeconfig-mode=644 --node-name=ec2-1" \
  sh -

# Wait for the API to come up
sleep 8
sudo k3s kubectl get nodes

echo
echo "==> Installing Docker (for building images locally)"
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
echo "==> Symlinking kubectl + writing kubeconfig to \$HOME"
sudo ln -sf /usr/local/bin/k3s /usr/local/bin/kubectl 2>/dev/null || true
mkdir -p "$HOME/.kube"
sudo cp /etc/rancher/k3s/k3s.yaml "$HOME/.kube/config"
sudo chown "$(id -u):$(id -g)" "$HOME/.kube/config"

PRIVATE_IP=$(hostname -I | awk '{print $1}')
NODE_TOKEN=$(sudo cat /var/lib/rancher/k3s/server/node-token)

echo
echo "================================================================"
echo "  k3s server is ready."
echo "  Run THIS command on EC2-2 and EC2-3:"
echo
echo "    K3S_URL=https://${PRIVATE_IP}:6443 \\"
echo "    K3S_TOKEN=${NODE_TOKEN} \\"
echo "    NODE_NAME=ec2-2 \\"   # change to ec2-3 on the third instance
echo "      bash infra/scripts/bootstrap-k3s-agent.sh"
echo "================================================================"
