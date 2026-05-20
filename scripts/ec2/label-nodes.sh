#!/usr/bin/env bash
set -euo pipefail

EC2_1_NODE="${EC2_1_NODE:-ec2-1}"
EC2_2_NODE="${EC2_2_NODE:-ec2-2}"
EC2_3_NODE="${EC2_3_NODE:-ec2-3}"

kubectl label node "$EC2_1_NODE" role=admin --overwrite
kubectl label node "$EC2_2_NODE" role=student --overwrite
kubectl label node "$EC2_3_NODE" role=teacher --overwrite

kubectl taint nodes "$EC2_1_NODE" node-role.kubernetes.io/control-plane- || true
kubectl taint nodes "$EC2_1_NODE" node-role.kubernetes.io/master- || true

kubectl get nodes --show-labels
