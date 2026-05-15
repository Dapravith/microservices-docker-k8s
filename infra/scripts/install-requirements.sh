#!/usr/bin/env bash
# Install Docker, containerd, kubeadm, kubelet, and kubectl on Ubuntu EC2.
# Run this script on every EC2 Kubernetes node before kubeadm init/join.
#
# Usage:
#   chmod +x install-requirements.sh
#   ./install-requirements.sh
#
# Optional:
#   NODE_NAME=ec2-master ./install-requirements.sh
#   NODE_NAME=ec2-worker-1 ./install-requirements.sh
#   K8S_VERSION=1.36 ./install-requirements.sh

set -euo pipefail

K8S_VERSION="${K8S_VERSION:-1.30}"
NODE_NAME="${NODE_NAME:-}"
DOCKER_USER="${DOCKER_USER:-ubuntu}"

echo "=================================================="
echo " Kubernetes EC2 Requirement Installer"
echo " Kubernetes version: v${K8S_VERSION}"
echo " Current host: $(hostname)"
echo "=================================================="

if [[ "$(uname -s)" == "Darwin" ]]; then
  echo "ERROR: This script must run on Ubuntu EC2, not macOS."
  echo ""
  echo "Copy it to EC2 first:"
  echo "  scp -i your-key.pem ./install-requirements.sh ubuntu@EC2_PUBLIC_IP:/home/ubuntu/"
  echo "  ssh -i your-key.pem ubuntu@EC2_PUBLIC_IP"
  echo "  chmod +x install-requirements.sh"
  echo "  ./install-requirements.sh"
  exit 1
fi

if [[ "$(id -u)" -ne 0 ]]; then
  echo "==> Re-running as root..."
  exec sudo env \
    NODE_NAME="${NODE_NAME}" \
    K8S_VERSION="${K8S_VERSION}" \
    DOCKER_USER="${DOCKER_USER}" \
    bash "$0" "$@"
fi

if [[ -n "${NODE_NAME}" ]]; then
  echo "==> Setting hostname to ${NODE_NAME}"
  hostnamectl set-hostname "${NODE_NAME}"
fi

echo "==> Checking Ubuntu"

if [[ ! -f /etc/os-release ]]; then
  echo "ERROR: Cannot detect Linux OS."
  exit 1
fi

. /etc/os-release

if [[ "${ID}" != "ubuntu" ]]; then
  echo "ERROR: This script supports Ubuntu only."
  echo "Detected OS: ${ID}"
  exit 1
fi

echo "Detected Ubuntu: ${VERSION_CODENAME}"

echo "==> Checking k3s conflict"

if command -v k3s >/dev/null 2>&1; then
  echo "ERROR: k3s is already installed."
  echo "Do not mix k3s and kubeadm."
  echo "Remove k3s first:"
  echo "  /usr/local/bin/k3s-uninstall.sh"
  exit 1
fi

echo "==> Updating packages"

apt-get update -y

apt-get install -y \
  ca-certificates \
  curl \
  gpg \
  apt-transport-https \
  git \
  software-properties-common

echo "==> Disabling swap"

swapoff -a
sed -ri '/\sswap\s/s/^/#/' /etc/fstab

echo "==> Loading Kubernetes kernel modules"

cat > /etc/modules-load.d/k8s.conf <<EOF
overlay
br_netfilter
EOF

modprobe overlay
modprobe br_netfilter

echo "==> Applying Kubernetes networking config"

cat > /etc/sysctl.d/k8s.conf <<EOF
net.bridge.bridge-nf-call-iptables  = 1
net.bridge.bridge-nf-call-ip6tables = 1
net.ipv4.ip_forward                 = 1
EOF

sysctl --system >/dev/null

echo "==> Installing Docker and containerd"

install -m 0755 -d /etc/apt/keyrings

rm -f /etc/apt/keyrings/docker.asc
rm -f /etc/apt/sources.list.d/docker.list

curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
  -o /etc/apt/keyrings/docker.asc

chmod a+r /etc/apt/keyrings/docker.asc

cat > /etc/apt/sources.list.d/docker.list <<EOF
deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu ${VERSION_CODENAME} stable
EOF

apt-get update -y

apt-get install -y \
  containerd.io \
  docker-ce \
  docker-ce-cli \
  docker-buildx-plugin \
  docker-compose-plugin

echo "==> Starting Docker and containerd"

systemctl enable --now docker
systemctl enable --now containerd

echo "==> Adding ${DOCKER_USER} to docker group"

if id "${DOCKER_USER}" >/dev/null 2>&1; then
  usermod -aG docker "${DOCKER_USER}"
else
  echo "WARNING: User ${DOCKER_USER} does not exist. Skipped docker group update."
fi

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

echo "==> Installing kubeadm, kubelet, and kubectl"

mkdir -p -m 755 /etc/apt/keyrings

rm -f /etc/apt/keyrings/kubernetes-apt-keyring.gpg
rm -f /etc/apt/sources.list.d/kubernetes.list

curl -fsSL "https://pkgs.k8s.io/core:/stable:/v${K8S_VERSION}/deb/Release.key" \
  | gpg --dearmor -o /etc/apt/keyrings/kubernetes-apt-keyring.gpg

cat > /etc/apt/sources.list.d/kubernetes.list <<EOF
deb [signed-by=/etc/apt/keyrings/kubernetes-apt-keyring.gpg] https://pkgs.k8s.io/core:/stable:/v${K8S_VERSION}/deb/ /
EOF

apt-get update -y

apt-get install -y kubelet kubeadm kubectl

apt-mark hold kubelet kubeadm kubectl

systemctl enable --now kubelet

echo "==> Verification"

echo ""
echo "Hostname:"
hostname

echo ""
echo "IP:"
hostname -I || true

echo ""
echo "Docker:"
docker --version

echo ""
echo "Containerd:"
containerd --version

echo ""
echo "Kubeadm:"
kubeadm version

echo ""
echo "Kubectl:"
kubectl version --client

echo ""
echo "Kubelet:"
kubelet --version

echo ""
echo "crictl:"
if crictl info >/dev/null 2>&1; then
  echo "crictl OK"
else
  echo "WARNING: crictl failed. Check containerd."
fi

echo ""
echo "Kernel modules:"
lsmod | grep -E 'overlay|br_netfilter' || true

echo ""
echo "Kubernetes networking:"
sysctl net.bridge.bridge-nf-call-iptables
sysctl net.bridge.bridge-nf-call-ip6tables
sysctl net.ipv4.ip_forward

echo ""
echo "Swap:"
free -h | grep Swap

echo ""
echo "Services:"
echo "docker:     $(systemctl is-active docker || true)"
echo "containerd: $(systemctl is-active containerd || true)"
echo "kubelet:    $(systemctl is-active kubelet || true)"

echo ""
echo "=================================================="
echo "Installation completed on $(hostname)"
echo "Logout and SSH back in to refresh Docker group permission."
echo "=================================================="