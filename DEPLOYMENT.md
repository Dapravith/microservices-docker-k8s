# Deployment guide — three AWS EC2 instances

This walks through bringing up the cluster, deploying the four services,
and capturing every screenshot the assignment asks for.

## 0. Prerequisites

* Three EC2 instances (Ubuntu 22.04+, t3.medium or larger, security group
  allows inter-node 6443/tcp + 10250/tcp + flannel UDP 8472/udp + the
  NodePort range 30000-32767 from your laptop's IP).
* A Docker Hub (or any) registry username — used as the image prefix.
* `kubectl` on your laptop, plus an SSH key that can reach all three EC2s.

| Role  | Hostname (example) | Public IP        | Purpose                        |
| ----- | ------------------ | ---------------- | ------------------------------ |
| EC2-1 | `ec2-1`            | `54.x.x.1`       | control plane + gateway/login + mongo |
| EC2-2 | `ec2-2`            | `54.x.x.2`       | student-service (Vol 1)        |
| EC2-3 | `ec2-3`            | `54.x.x.3`       | teacher-service (Vol 2)        |

## 1. Per-node bootstrap (run on **every** EC2)

```bash
scp infra/scripts/bootstrap-ec2.sh ubuntu@ec2-1:~
ssh ubuntu@ec2-1 'sudo bash bootstrap-ec2.sh'
# repeat for ec2-2, ec2-3
```

📷 **Screenshot 1 — three EC2 instances**: AWS console showing all three
in the *Running* state.

## 2. Initialise the control plane (EC2-1 only)

```bash
ssh ubuntu@ec2-1 'bash -s' < infra/scripts/init-control-plane.sh
```

The script ends by printing a `kubeadm join …` command. Run it on EC2-2
and EC2-3:

```bash
ssh ubuntu@ec2-2 'sudo kubeadm join …'
ssh ubuntu@ec2-3 'sudo kubeadm join …'
```

Verify:

```bash
ssh ubuntu@ec2-1 'kubectl get nodes -o wide'
```

All three nodes should be `Ready`.

## 3. Pin pods to the right node

Pre-create the MongoDB host directory on EC2-1 (the PV uses `hostPath`):

```bash
ssh ubuntu@ec2-1 'sudo mkdir -p /var/lib/mongo-data && sudo chmod 700 /var/lib/mongo-data'
```

Find the kubectl-visible names and label them:

```bash
ssh ubuntu@ec2-1 'kubectl get nodes -o name'
# Output looks like  node/ip-10-0-0-1, node/ip-10-0-0-2, node/ip-10-0-0-3

EC2_1=ip-10-0-0-1 \
EC2_2=ip-10-0-0-2 \
EC2_3=ip-10-0-0-3 \
ssh ubuntu@ec2-1 'bash -s' < infra/scripts/label-nodes.sh
```

## 4. Build and push images

From your laptop or build host:

```bash
DOCKERHUB_USER=youruser bash infra/scripts/build-and-push.sh
```

📷 **Screenshot 2 — `docker images` / `docker ps`** on each EC2 after
the cluster has scheduled the pods (run `docker ps` on each node).

## 5. Apply the manifests

On EC2-1 (or wherever your kubectl points at the cluster):

```bash
sed -i.bak "s|DOCKERHUB_USER|youruser|g" k8s/*.yaml
sed -i.bak "s|REPLACE_WITH_AT_LEAST_32_CHARACTERS_OF_RANDOM_BYTES|$(openssl rand -hex 32)|" k8s/01-secrets.yaml

bash infra/scripts/apply-k8s.sh
```

Validate:

```bash
kubectl -n msp get deploy,svc,statefulset
kubectl -n msp get pods -o wide
```

📷 **Screenshot 3 — Services and Deployments**: output of
`kubectl -n msp get deploy,svc -o wide`.

📷 **Screenshot 4 — Pods on each EC2**: filter by node:

```bash
kubectl -n msp get pods -o wide --field-selector spec.nodeName=$EC2_1
kubectl -n msp get pods -o wide --field-selector spec.nodeName=$EC2_2
kubectl -n msp get pods -o wide --field-selector spec.nodeName=$EC2_3
```

## 6. Smoke-test from Postman

Set the `gatewayUrl` collection variable to `http://<EC2-1 public IP>:30000`
and run the requests in order:

| # | Request                                     | Expected | Screenshot                  |
| - | ------------------------------------------- | -------- | --------------------------- |
| 1 | `POST /login` (student)                     | 200 + token | 5. Login → JWT token       |
| 2 | `POST /login` (teacher)                     | 200 + token | (same shape)               |
| 3 | `POST /student/submitassignment` w/ student | 201 + Mongo doc | 6. /student with student JWT |
| 4 | `POST /teacher/addassignment` w/ teacher    | 201 + Mongo doc | 7. /teacher with teacher JWT |
| 5 | `GET /student/viewassignment` w/ teacher    | **403**  | 8. /student with teacher JWT |
| 6 | `GET /teacher/searchstudent` w/ student     | **403**  | 9. /teacher with student JWT |

Database activity — confirm with mongosh:

```bash
kubectl -n msp exec -it statefulset/mongo -- mongosh --quiet --eval '
  printjson(db.getSiblingDB("auth_db").users.countDocuments({}));
  printjson(db.getSiblingDB("student_db").assignments.find().toArray());
  printjson(db.getSiblingDB("teacher_db").teacher_assignments.find().toArray());
'
```

## 7. Tear down

```bash
kubectl delete ns msp
# (delete the EC2 instances from the AWS console when done)
```

## Troubleshooting

* **CrashLoopBackOff on a Spring service** — usually MongoDB isn't ready
  yet. Check `kubectl -n msp logs statefulset/mongo` and verify the PV is
  Bound: `kubectl get pv,pvc -A`.
* **403 on every request** — the JWT secret in `app-secrets` doesn't
  match the one the login pod is using. Re-apply `01-secrets.yaml` and
  `kubectl -n msp rollout restart deploy/login-service deploy/api-gateway`.
* **Pods scheduled on the wrong node** — the node labels were not
  applied. Re-run `infra/scripts/label-nodes.sh`.
* **`hostPath` PV not binding** — the directory `/var/lib/mongo-data`
  doesn't exist on the labeled `db=mongo` node. Create it with
  `sudo mkdir -p` and re-apply `05-mongodb.yaml`.
