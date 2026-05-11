#!/usr/bin/env bash
# Run ONCE on EC2-1 control-plane after EC2-2 and EC2-3 joined the cluster.
# Labels each Kubernetes node so pods can be pinned to the correct EC2 instance.

set -euo pipefail

EC2_1="${EC2_1:?set EC2_1 to the kubectl-visible control-plane node name}"
EC2_2="${EC2_2:?set EC2_2 to the kubectl-visible student worker node name}"
EC2_3="${EC2_3:?set EC2_3 to the kubectl-visible teacher worker node name}"

echo "Applying labels..."
kubectl label --overwrite node "$EC2_1" role=frontend db=mongo
kubectl label --overwrite node "$EC2_2" role=student
kubectl label --overwrite node "$EC2_3" role=teacher

echo "Removing control-plane taints for lab workload scheduling..."
kubectl taint node "$EC2_1" node-role.kubernetes.io/control-plane- 2>/dev/null || true
kubectl taint node "$EC2_1" node-role.kubernetes.io/master- 2>/dev/null || true

echo "Creating MongoDB hostPath directory on EC2-1..."
sudo mkdir -p /var/lib/mongo-data
sudo chmod 700 /var/lib/mongo-data

echo "Labels applied:"
kubectl get nodes -L role,db