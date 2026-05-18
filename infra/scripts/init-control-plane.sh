#!/usr/bin/env bash
# Run ONLY on the Kubernetes control-plane/master EC2.
# Do NOT run this on worker nodes.
set -euo pipefail

POD_CIDR="${POD_CIDR:-10.244.0.0/16}"
IGNORE_PREFLIGHT_ERRORS="${IGNORE_PREFLIGHT_ERRORS:-Mem}"

if ! command -v kubeadm >/dev/null 2>&1; then
  echo "ERROR: kubeadm is not installed."
  exit 1
fi

if ! command -v kubectl >/dev/null 2>&1; then
  echo "ERROR: kubectl is not installed."
  exit 1
fi

if [[ "$(uname -s)" != "Linux" ]]; then
  echo "ERROR: This script must run on Linux EC2."
  exit 1
fi

CONTROL_PLANE_IP="${CONTROL_PLANE_IP:-$(ip route get 1.1.1.1 | awk '{for(i=1;i<=NF;i++) if($i=="src") {print $(i+1); exit}}')}"

if [[ -z "${CONTROL_PLANE_IP}" ]]; then
  echo "ERROR: Could not detect private IP."
  echo "Run manually like this:"
  echo "CONTROL_PLANE_IP=<private-ip> bash init-control-plane.sh"
  exit 1
fi

echo "Control-plane IP: ${CONTROL_PLANE_IP}"
echo "Pod CIDR: ${POD_CIDR}"
echo "Ignore preflight errors: ${IGNORE_PREFLIGHT_ERRORS}"
echo

# If control-plane already exists, just fix kubeconfig and print join command.
if [[ -f /etc/kubernetes/admin.conf ]]; then
  echo "Kubernetes control-plane already initialized."
  echo "Fixing kubeconfig..."

  mkdir -p "$HOME/.kube"
  sudo cp -f /etc/kubernetes/admin.conf "$HOME/.kube/config"
  sudo chown "$(id -u):$(id -g)" "$HOME/.kube/config"
  chmod 600 "$HOME/.kube/config"

  echo
  echo "Checking cluster nodes..."
  kubectl get nodes -o wide || true

  echo
  echo "Installing or updating Flannel CNI..."
  kubectl apply -f https://github.com/flannel-io/flannel/releases/latest/download/kube-flannel.yml || true

  echo
  echo "=== Worker join command for EC2-2 and EC2-3 ==="
  echo "sudo $(sudo kubeadm token create --print-join-command) --ignore-preflight-errors=${IGNORE_PREFLIGHT_ERRORS}"
  echo "==============================================="
  exit 0
fi

# If kubelet.conf exists but admin.conf does not, this node has partial/old Kubernetes state.
if [[ -f /etc/kubernetes/kubelet.conf ]]; then
  echo "ERROR: This node has existing Kubernetes worker/control-plane files but no admin.conf."
  echo "If this is meant to be the control-plane, reset it first:"
  echo
  echo "sudo kubeadm reset -f"
  echo "sudo rm -rf /etc/kubernetes ~/.kube /var/lib/etcd /etc/cni/net.d"
  echo "sudo systemctl restart containerd kubelet"
  echo
  echo "Then run this script again."
  exit 1
fi

echo "Initializing Kubernetes control-plane..."

sudo kubeadm init \
  --pod-network-cidr="${POD_CIDR}" \
  --apiserver-advertise-address="${CONTROL_PLANE_IP}" \
  --cri-socket unix:///run/containerd/containerd.sock \
  --ignore-preflight-errors="${IGNORE_PREFLIGHT_ERRORS}"

echo
echo "Configuring kubeconfig..."

mkdir -p "$HOME/.kube"
sudo cp -f /etc/kubernetes/admin.conf "$HOME/.kube/config"
sudo chown "$(id -u):$(id -g)" "$HOME/.kube/config"
chmod 600 "$HOME/.kube/config"

echo
echo "Installing Flannel CNI..."

kubectl apply -f https://github.com/flannel-io/flannel/releases/latest/download/kube-flannel.yml

echo
echo "Waiting for node..."
sleep 30

kubectl get nodes -o wide
kubectl get pods -A

echo
echo "=== Worker join command for EC2-2 and EC2-3 ==="
echo "sudo $(sudo kubeadm token create --print-join-command) --ignore-preflight-errors=${IGNORE_PREFLIGHT_ERRORS}"
echo "==============================================="