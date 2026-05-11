#!/usr/bin/env bash
# Run on every EC2 node.
# Installs Docker, containerd, kubeadm, kubelet, kubectl.
set -euo pipefail

K8S_VERSION="${K8S_VERSION:-1.30}"

echo "==> Updating apt packages"
sudo apt-get update -y
sudo apt-get install -y ca-certificates curl gpg apt-transport-https

echo "==> Installing Docker/containerd repo"
sudo install -m 0755 -d /etc/apt/keyrings
sudo rm -f /etc/apt/keyrings/docker.asc

sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
  -o /etc/apt/keyrings/docker.asc

sudo chmod a+r /etc/apt/keyrings/docker.asc

echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

sudo apt-get update -y
sudo apt-get install -y containerd.io docker-ce docker-ce-cli docker-buildx-plugin

echo "==> Starting Docker and containerd"
sudo systemctl enable --now docker
sudo systemctl enable --now containerd

echo "==> Adding ubuntu user to docker group"
sudo usermod -aG docker ubuntu || true

echo "==> Configuring kernel modules"
cat <<EOF | sudo tee /etc/modules-load.d/k8s.conf
overlay
br_netfilter
EOF

sudo modprobe overlay
sudo modprobe br_netfilter

echo "==> Configuring sysctl"
cat <<EOF | sudo tee /etc/sysctl.d/k8s.conf
net.bridge.bridge-nf-call-iptables  = 1
net.ipv4.ip_forward                 = 1
net.bridge.bridge-nf-call-ip6tables = 1
EOF

sudo sysctl --system >/dev/null

echo "==> Disabling swap"
sudo swapoff -a
sudo sed -ri '/\sswap\s/s/^/#/' /etc/fstab

echo "==> Configuring containerd SystemdCgroup=true"
sudo mkdir -p /etc/containerd
containerd config default | sudo tee /etc/containerd/config.toml >/dev/null
sudo sed -ri 's/SystemdCgroup = false/SystemdCgroup = true/' /etc/containerd/config.toml
sudo systemctl restart containerd

echo "==> Configuring crictl"
cat <<EOF | sudo tee /etc/crictl.yaml
runtime-endpoint: unix:///run/containerd/containerd.sock
image-endpoint: unix:///run/containerd/containerd.sock
timeout: 10
debug: false
EOF

echo "==> Installing kubeadm/kubelet/kubectl v${K8S_VERSION}"
sudo rm -f /etc/apt/keyrings/kubernetes-apt-keyring.gpg

curl -fsSL "https://pkgs.k8s.io/core:/stable:/v${K8S_VERSION}/deb/Release.key" \
  | sudo gpg --dearmor -o /etc/apt/keyrings/kubernetes-apt-keyring.gpg

echo "deb [signed-by=/etc/apt/keyrings/kubernetes-apt-keyring.gpg] https://pkgs.k8s.io/core:/stable:/v${K8S_VERSION}/deb/ /" \
  | sudo tee /etc/apt/sources.list.d/kubernetes.list

sudo apt-get update -y
sudo apt-get install -y kubelet kubeadm kubectl
sudo apt-mark hold kubelet kubeadm kubectl

sudo systemctl enable --now kubelet

echo "==> Verifying installation"
docker --version || true
containerd --version || true
kubeadm version || true
kubectl version --client || true
sudo crictl info >/dev/null && echo "crictl OK"

echo "bootstrap-ec2.sh complete on $(hostname)"
echo "IMPORTANT: log out and SSH back in if docker permission still fails."