#!/usr/bin/env bash
# Install Kubernetes requirements on Ubuntu EC2 nodes.
# Run on EC2-1, EC2-2, and EC2-3 before kubeadm init/join.

set -euo pipefail

K8S_VERSION="${K8S_VERSION:-1.30}"
NODE_NAME="${NODE_NAME:-}"

echo "=================================================="
echo " Kubernetes EC2 Requirement Installer"
echo " Kubernetes version: v${K8S_VERSION}"
echo " Current host: $(hostname)"
echo "=================================================="

if [[ "$(id -u)" -ne 0 ]]; then
  echo "ERROR: Run with sudo:"
  echo "  sudo NODE_NAME=ec2-1 bash install-k8s-requirements.sh"
  exit 1
fi

if [[ -n "${NODE_NAME}" ]]; then
  echo "==> Setting hostname to: ${NODE_NAME}"
  hostnamectl set-hostname "${NODE_NAME}"
fi

echo "==> Checking for k3s conflict"
if command -v k3s >/dev/null 2>&1; then
  echo "ERROR: k3s is installed on this node."
  echo "Do not mix k3s and kubeadm."
  echo "Remove k3s first if you want to use kubeadm:"
  echo "  sudo /usr/local/bin/k3s-uninstall.sh"
  echo "Then rerun this script."
  exit 1
fi

echo "==> Updating packages"
apt-get update -y
apt-get install -y ca-certificates curl gpg apt-transport-https git software-properties-common

echo "==> Installing Docker and containerd"
install -m 0755 -d /etc/apt/keyrings
rm -f /etc/apt/keyrings/docker.asc

curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
  -o /etc/apt/keyrings/docker.asc

chmod a+r /etc/apt/keyrings/docker.asc

echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
  > /etc/apt/sources.list.d/docker.list

apt-get update -y
apt-get install -y containerd.io docker-ce docker-ce-cli docker-buildx-plugin docker-compose-plugin

echo "==> Starting Docker and containerd"
systemctl enable --now docker
systemctl enable --now containerd

echo "==> Allowing ubuntu user to run Docker"
usermod -aG docker ubuntu || true

echo "==> Configuring containerd for Kubernetes"
mkdir -p /etc/containerd
containerd config default > /etc/containerd/config.toml

sed -ri 's/SystemdCgroup = false/SystemdCgroup = true/' /etc/containerd/config.toml

systemctl restart containerd

echo "==> Configuring crictl"
cat > /etc/crictl.yaml <<EOF
runtime-endpoint: unix:///run/containerd/containerd.sock
image-endpoint: unix:///run/containerd/containerd.sock
timeout: 10
debug: false
EOF

echo "==> Loading Kubernetes kernel modules"
cat > /etc/modules-load.d/k8s.conf <<EOF
overlay
br_netfilter
EOF

modprobe overlay
modprobe br_netfilter

echo "==> Applying Kubernetes sysctl settings"
cat > /etc/sysctl.d/k8s.conf <<EOF
net.bridge.bridge-nf-call-iptables  = 1
net.ipv4.ip_forward                 = 1
net.bridge.bridge-nf-call-ip6tables = 1
EOF

sysctl --system >/dev/null

echo "==> Disabling swap"
swapoff -a
sed -ri '/\sswap\s/s/^/#/' /etc/fstab

echo "==> Installing kubeadm, kubelet, kubectl v${K8S_VERSION}"
mkdir -p -m 755 /etc/apt/keyrings
rm -f /etc/apt/keyrings/kubernetes-apt-keyring.gpg

curl -fsSL "https://pkgs.k8s.io/core:/stable:/v${K8S_VERSION}/deb/Release.key" \
  | gpg --dearmor -o /etc/apt/keyrings/kubernetes-apt-keyring.gpg

echo "deb [signed-by=/etc/apt/keyrings/kubernetes-apt-keyring.gpg] https://pkgs.k8s.io/core:/stable:/v${K8S_VERSION}/deb/ /" \
  > /etc/apt/sources.list.d/kubernetes.list

apt-get update -y
apt-get install -y kubelet kubeadm kubectl
apt-mark hold kubelet kubeadm kubectl

systemctl enable --now kubelet

echo "==> Verification"
echo "Hostname:"
hostname

echo ""
echo "IP:"
hostname -I

echo ""
docker --version
containerd --version
kubeadm version
kubectl version --client
crictl info >/dev/null && echo "crictl OK"

echo ""
echo "Swap status:"
free -h | grep Swap

echo ""
echo "=================================================="
echo "Requirement installation completed on $(hostname)"
echo "IMPORTANT: logout and SSH back in to refresh Docker group permission."
echo "=================================================="