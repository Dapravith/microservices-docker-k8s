#!/usr/bin/env bash
# Run ONCE on the control plane after both workers have joined. Labels each
# node so the Deployments' nodeAffinity rules can pin pods to the right EC2.
#
#   EC2-1  →  role=frontend  (api-gateway + login-service)  AND  db=mongo
#   EC2-2  →  role=student   (student-service + Vol1)
#   EC2-3  →  role=teacher   (teacher-service + Vol2)
#
# The control plane is taint-free in this lab so it can host workloads. If
# you want a dedicated DB node, give that node `db=mongo` instead of EC2-1.

set -euo pipefail

EC2_1="${EC2_1:?set EC2_1 to the kubectl-visible name of the control plane / frontend node}"
EC2_2="${EC2_2:?set EC2_2 to the kubectl-visible name of the student worker}"
EC2_3="${EC2_3:?set EC2_3 to the kubectl-visible name of the teacher worker}"

kubectl label --overwrite node "$EC2_1" role=frontend db=mongo
kubectl label --overwrite node "$EC2_2" role=student
kubectl label --overwrite node "$EC2_3" role=teacher

# Allow scheduling on the control plane (so EC2-1 can host gateway/login/mongo).
kubectl taint node "$EC2_1" node-role.kubernetes.io/control-plane- 2>/dev/null || true

# The MongoDB PV uses hostPath /var/lib/mongo-data on the db=mongo node.
# Make sure that directory exists on EC2-1 before applying 05-mongodb.yaml:
#   ssh ec2-1 'sudo mkdir -p /var/lib/mongo-data && sudo chmod 700 /var/lib/mongo-data'

echo "Labels applied:"
kubectl get nodes --show-labels
