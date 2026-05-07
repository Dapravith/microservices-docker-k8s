#!/usr/bin/env bash
# Apply manifests in numeric order. Run from the repo root after labelling
# nodes and pushing images, e.g.:
#   sed -i.bak "s|DOCKERHUB_USER|youruser|g" k8s/*.yaml
#   ./infra/scripts/apply-k8s.sh
set -euo pipefail

kubectl apply -f k8s/00-namespace.yaml
kubectl apply -f k8s/01-secrets.yaml
kubectl apply -f k8s/05-mongodb.yaml
kubectl -n msp rollout status statefulset/mongo --timeout=180s

kubectl apply -f k8s/10-login.yaml
kubectl apply -f k8s/20-student.yaml
kubectl apply -f k8s/30-teacher.yaml
kubectl apply -f k8s/40-gateway.yaml

kubectl -n msp rollout status deploy/login-service   --timeout=180s
kubectl -n msp rollout status deploy/student-service --timeout=180s
kubectl -n msp rollout status deploy/teacher-service --timeout=180s
kubectl -n msp rollout status deploy/api-gateway     --timeout=180s

echo
echo "Cluster state:"
kubectl -n msp get pods -o wide
echo
echo "Reach the gateway at http://<EC2-1 public IP>:30000"
