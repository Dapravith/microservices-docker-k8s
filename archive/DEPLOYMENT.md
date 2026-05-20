# Deployment guide — three AWS EC2 instances

This walks through bringing up the cluster, deploying the five services,
and capturing every screenshot the assignment asks for.

## Required screenshots (assignment deliverables)

| # | Requirement | Captured in step | Command(s) |
| - | ----------- | ---------------- | ---------- |
| 1 | **3 EC2 instances** running in AWS | §1 | AWS console → EC2 → Instances (filter: *Running*) |
| 2 | **Docker image / container on each of the 3 EC2 instances** | §4 + §5 | `sudo crictl images` and `sudo crictl ps` on EC2-1, EC2-2, EC2-3 |
| 3 | **3 Services and Deployments** across the 3 EC2 instances | §5 | `kubectl -n msp get deploy,svc -o wide` |
| 4 | **Pods on each EC2 instance** | §5 | `kubectl -n msp get pods -o wide --field-selector spec.nodeName=<node>` for each of the 3 nodes |

> ⚠️ This cluster uses **containerd** (Kubernetes ≥ 1.24 dropped dockershim),
> so `docker ps` on the nodes will **not** list pod containers. Use
> `sudo crictl ps` / `sudo crictl images` instead — those talk to the
> same runtime kubelet uses.

## Implementation guide at a glance

End-to-end steps, in order. Each one links to the detailed section below.

1. **§0 Prerequisites** — provision 3 EC2 instances, open security-group ports,
   install `kubectl` locally.
2. **§1 Bootstrap nodes** — run `install-requirements.sh` on every EC2.
3. **§2 Init cluster** — `kubeadm init` on EC2-1, then `kubeadm join` on EC2-2 / EC2-3.
4. **§3 Label nodes + prep host path** — `mkdir /var/lib/mongo-data` on EC2-1
   and label nodes with `db=mongo`, `role=frontend|student|teacher`.
5. **§4 Build & push images** — `build-and-push.sh DOCKERHUB_USER=…`.
6. **§5 Apply manifests** — substitute `DOCKERHUB_USER`, rotate the JWT
   secret, then `apply-k8s.sh` applies the 14 files in the right order.
7. **§6 Smoke-test** — run the Postman collection against the gateway NodePort.
8. **§7 (optional) SonarQube scan** · **§8 Tear down**.

The k8s/ folder now follows a one-file-per-resource layout:

```
k8s/
├── 00-namespace.yaml          # namespace + StorageClass
├── 01-secrets.yaml            # JWT + Mongo URIs
├── mongodb.yaml               # PV + PVC + StatefulSet
├── mongodb-service.yaml       # headless Service
├── auth.yaml                  # authentication-service Deployment
├── auth-service.yaml
├── registration.yaml
├── registration-service.yaml
├── student.yaml
├── student-service.yaml
├── teacher.yaml
├── teacher-service.yaml
├── api-gateway.yaml
└── api-gateway-service.yaml   # NodePort 30000
```

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

The `install-requirements.sh` script self-elevates with sudo, validates
Ubuntu, installs containerd/Docker/kubeadm/kubelet/kubectl (Kubernetes
v1.30 by default), and prints a verification summary at the end.

```bash
scp infra/scripts/install-requirements.sh ubuntu@ec2-1:~
ssh ubuntu@ec2-1 'bash install-requirements.sh'
# repeat for ec2-2 and ec2-3
```

Optional overrides:

```bash
# Pin a different Kubernetes minor version
K8S_VERSION=1.31 ssh ubuntu@ec2-1 'K8S_VERSION=$K8S_VERSION bash install-requirements.sh'

# Set the node hostname while you're at it (handy for kubeadm)
NODE_NAME=ec2-1 ssh ubuntu@ec2-1 'NODE_NAME=$NODE_NAME bash install-requirements.sh'
```

After it finishes, **log out and SSH back in** so the new `docker`
group membership takes effect.

📷 **Screenshot 1 (requirement #1) — three EC2 instances**: AWS console
→ EC2 → Instances, filtered to *Running*. All three (ec2-1, ec2-2, ec2-3)
must be visible in a single screenshot.

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

📷 **Screenshot 2 (requirement #2) — image + container on each EC2**.
After the cluster has scheduled the pods, SSH into each node and capture
both commands:

```bash
ssh ubuntu@ec2-1 'sudo crictl images && echo "---" && sudo crictl ps'
ssh ubuntu@ec2-2 'sudo crictl images && echo "---" && sudo crictl ps'
ssh ubuntu@ec2-3 'sudo crictl images && echo "---" && sudo crictl ps'
```

Expected per node:
* **ec2-1**: api-gateway, registration, authentication-service, mongodb
* **ec2-2**: student-service
* **ec2-3**: teacher-service

(`docker ps` will be empty on the nodes — kubelet uses containerd, not
Docker. Use `crictl`.)

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

📷 **Screenshot 3 (requirement #3) — Services and Deployments**: a
single screenshot showing the output of
`kubectl -n msp get deploy,svc -o wide`. The `NODE` column (visible with
`-o wide`) is what proves the workloads are spread across the 3 EC2
instances.

📷 **Screenshot 4 (requirement #4) — Pods on each EC2**. First list the
nodes so you have their exact kubectl-visible names:

```bash
kubectl get nodes -o name
# e.g. node/ip-10-0-0-1, node/ip-10-0-0-2, node/ip-10-0-0-3
```

Then take one screenshot per node (substitute the real node names):

```bash
kubectl -n msp get pods -o wide --field-selector spec.nodeName=ip-10-0-0-1
kubectl -n msp get pods -o wide --field-selector spec.nodeName=ip-10-0-0-2
kubectl -n msp get pods -o wide --field-selector spec.nodeName=ip-10-0-0-3
```

Expected pods per node:
* **ec2-1** (`role=frontend, db=mongo`): api-gateway, registration,
  authentication-service, mongodb-0
* **ec2-2** (`role=student`): student-service
* **ec2-3** (`role=teacher`): teacher-service

## 6. Smoke-test from Postman

Set the `gatewayUrl` collection variable to `http://<EC2-1 public IP>:30000`
and run the requests in order:

| # | Request                                       | Expected         | Screenshot                  |
| - | --------------------------------------------- | ---------------- | --------------------------- |
| 1 | `POST /register/student` (alice)              | 201 + UserResp   | New                         |
| 2 | `POST /register/teacher` (ms.smith)           | 201 + UserResp   | New                         |
| 3 | `POST /login` (student)                       | 200 + token      | 5. Login → JWT token        |
| 4 | `POST /login` (teacher)                       | 200 + token      | (same shape)                |
| 5 | `POST /student/submitassignment` w/ student   | 201 + Mongo doc  | 6. /student with student JWT |
| 6 | `POST /teacher/addassignment` w/ teacher      | 201 + Mongo doc  | 7. /teacher with teacher JWT |
| 7 | `GET /student/viewassignment` w/ teacher      | **403**          | 8. /student with teacher JWT |
| 8 | `GET /teacher/searchstudent` w/ student       | **403**          | 9. /teacher with student JWT |

Database activity — confirm with mongosh:

```bash
kubectl -n msp exec -it statefulset/mongodb -- mongosh --quiet --eval '
  printjson(db.getSiblingDB("auth_db").users.countDocuments({}));
  printjson(db.getSiblingDB("student_db").assignments.find().toArray());
  printjson(db.getSiblingDB("teacher_db").teacher_assignments.find().toArray());
'
```

## 7. Code quality with SonarQube (optional but recommended)

Run a SonarQube scan against any branch before pushing:

```bash
docker-compose -f docker-compose.sonar.yml up -d        # http://localhost:9000
# admin/admin on first login, then: Account → Security → Generate Token
export SONAR_TOKEN=<token>
./infra/scripts/sonar-scan.sh
```

You get five Sonar projects (`microservices-k8s-registration`,
`-login`, `-student`, `-teacher`, `-gateway`), each with coverage,
duplications, security hotspots, and code smells.

Tear the local Sonar stack back down with:

```bash
docker-compose -f docker-compose.sonar.yml down
```

## 8. Tear down

```bash
kubectl delete ns msp
# (delete the EC2 instances from the AWS console when done)
```

## Troubleshooting

* **CrashLoopBackOff on a Spring service** — usually MongoDB isn't ready
  yet. Check `kubectl -n msp logs statefulset/mongodb` and verify the PV
  is Bound: `kubectl get pv,pvc -A`.
* **403 on every request** — the JWT secret in `app-secrets` doesn't
  match the one the auth pod is using. Re-apply `01-secrets.yaml` and
  `kubectl -n msp rollout restart deploy/authentication-service deploy/api-gateway`.
* **Pods scheduled on the wrong node** — the node labels were not
  applied. Re-run `infra/scripts/label-nodes.sh`.
* **`hostPath` PV not binding** — the directory `/var/lib/mongo-data`
  doesn't exist on the labeled `db=mongo` node. Create it with
  `sudo mkdir -p` and re-apply `mongodb.yaml`.
