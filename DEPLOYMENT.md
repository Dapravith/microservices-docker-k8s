# AWS Deployment Runbook

Step-by-step guide to deploy the AUPP microservices project across **3 EC2
instances** with a kubeadm Kubernetes cluster. Follow it top-to-bottom — every
command is copy-pasteable and every screenshot the assignment asks for is
called out as `SCREENSHOT N`.

## 0. What you'll end up with

| EC2  | Role     | Node label        | Pods on it                            |
| ---- | -------- | ----------------- | ------------------------------------- |
| EC2-1 | control plane | `role=frontend` | `api-gateway`, `auth-service`, `mongodb` |
| EC2-2 | worker        | `role=student`  | `student-service` + Vol1               |
| EC2-3 | worker        | `role=teacher`  | `teacher-service` + Vol2               |

A single NodePort service exposes the gateway on `:30080` of every EC2's
public IP — Postman calls that.

## 1. Provision 3 EC2 instances

In the AWS console:

1. **AMI:** Ubuntu Server 22.04 LTS (24.04 also works).
2. **Type:** `t3.medium` (2 vCPU, 4 GB RAM) for all three. `t2.micro` is too
   small for kubeadm + Spring Boot.
3. **Storage:** 20 GB gp3 each.
4. **Key pair:** one SSH key, used for all three nodes.
5. **Security group** (one shared SG is easiest):
   - 22/tcp from your laptop IP (SSH)
   - 6443/tcp within the SG (control plane API)
   - 10250/tcp within the SG (kubelet)
   - 8285,8472/udp within the SG (Flannel VXLAN)
   - 30000–32767/tcp from your laptop (NodePort range — opens 30080 too)
6. **Tag:** Name them `aupp-ec2-1`, `aupp-ec2-2`, `aupp-ec2-3`.

➡️  **SCREENSHOT 1: AWS EC2 console showing 3 running instances.**

## 2. Bootstrap every node

SSH into each EC2 and run:

```bash
git clone <your repo>            # or scp the project folder over
cd microservices-docker-k8s
sudo bash infra/scripts/bootstrap-ec2.sh
# log out + log back in so the docker group sticks
```

That installs containerd, Docker (for building images), kubeadm/kubelet/kubectl
v1.30, and configures kernel modules + sysctl.

## 3. Initialize the control plane on EC2-1

```bash
sudo bash infra/scripts/init-control-plane.sh
```

The script prints a `kubeadm join …` command at the end. Copy it.

## 4. Join EC2-2 and EC2-3 as workers

On **EC2-2** and **EC2-3**, paste the join command, e.g.:

```bash
sudo kubeadm join <EC2-1-private-IP>:6443 --token <token> \
     --discovery-token-ca-cert-hash sha256:<hash>
```

Back on EC2-1, confirm all three nodes are Ready:

```bash
kubectl get nodes -o wide
```

## 5. Label the nodes for pod placement

On EC2-1:

```bash
# names from `kubectl get nodes -o name | sed 's|node/||'`
EC2_1=aupp-ec2-1 EC2_2=aupp-ec2-2 EC2_3=aupp-ec2-3 \
  bash infra/scripts/label-nodes.sh
```

The script also removes the control-plane taint on EC2-1 so pods can land on
it.

## 6. Build the images

You have two options.

**Option A — push to Docker Hub** (recommended; works on any node):

```bash
docker login
REGISTRY=docker.io/<your-dockerhub-user> TAG=1.0.0 \
  bash infra/scripts/build-and-push.sh
```

Then deploy with the same prefix:

```bash
IMAGE_REPO=docker.io/<your-dockerhub-user> \
  bash infra/scripts/apply-k8s.sh
```

**Option B — build locally on each node** (no registry):

```bash
# Run on each EC2:
cd microservices-docker-k8s
docker build -t aupp/api-gateway:1.0.0      ./api-gateway
docker build -t aupp/auth-service:1.0.0     ./auth-service
docker build -t aupp/student-service:1.0.0  ./student-service
docker build -t aupp/teacher-service:1.0.0  ./teacher-service

# Make containerd see them (k8s talks to containerd, not Docker):
for img in aupp/api-gateway:1.0.0 aupp/auth-service:1.0.0 \
           aupp/student-service:1.0.0 aupp/teacher-service:1.0.0; do
  docker save "$img" | sudo ctr -n=k8s.io images import -
done
```

➡️  **SCREENSHOT 2: `docker images` on each of the 3 EC2 instances**, showing
the four service images.

## 7. Rotate the JWT secret

Open `k8s/01-secrets.yaml` and replace the `JWT_SECRET` placeholder with a
fresh value:

```bash
openssl rand -base64 64 | tr -d '\n'
```

## 8. Deploy everything to the cluster

On EC2-1:

```bash
bash infra/scripts/apply-k8s.sh
```

The script applies manifests in order, waits for each rollout, and prints the
final state.

➡️  **SCREENSHOT 3: `kubectl -n aupp get deploy,svc -o wide`** (services and
deployments across the 3 EC2 instances).

➡️  **SCREENSHOT 4: `kubectl -n aupp get pods -o wide`** showing one pod per
node (api-gateway+auth+mongodb on EC2-1, student on EC2-2, teacher on EC2-3).

## 9. Seed users and test from Postman

Get any EC2 public IP — call it `$GW`.

In Postman, import `postman/microservices-k8s.postman_collection.json` and set
the collection variable `gateway` to `http://$GW:30080`. Then run:

| Step | Request                                  | Expected            | Screenshot |
| ---- | ---------------------------------------- | ------------------- | ---------- |
| a    | `POST /auth/register` (student)          | `201 Created`       |            |
| b    | `POST /auth/register` (teacher)          | `201 Created`       |            |
| c    | `POST /auth/login` (student)             | `200` + JWT         | **SCREENSHOT 5** |
| d    | `POST /auth/login` (teacher)             | `200` + JWT         |            |
| e    | `GET /student/me` with **student** JWT   | `200`               | **SCREENSHOT 6** |
| f    | `GET /teacher/me` with **teacher** JWT   | `200`               | **SCREENSHOT 7** |
| g    | `GET /student/me` with **teacher** JWT   | `403 Forbidden`     | **SCREENSHOT 8** |
| h    | `GET /teacher/me` with **student** JWT   | `403 Forbidden`     | **SCREENSHOT 9** |

The Postman collection automatically captures each JWT into the
`studentJwt` / `teacherJwt` collection variables.

To exercise real DB writes (the spec says "should have some database
activity"), run `POST /student` (with student JWT) and `POST /teacher`
(with teacher JWT) — both create a record.

## 10. Tear-down

```bash
kubectl delete namespace aupp     # wipes deployments, services, PVCs
# then terminate the EC2 instances from the console
```

## Troubleshooting

- **Pods stuck `ImagePullBackOff`** → using Option B above; make sure you ran
  `ctr -n=k8s.io images import` on **the right node** (the one with the
  `nodeSelector` matching the pod).
- **`auth-service` 500s on login** → JWT secret on gateway and auth service
  don't match. They must both come from the same `JWT_SECRET` env var.
- **Pods `Pending`** → `kubectl describe pod` to see if it's "no node with
  matching role". Re-run `label-nodes.sh`.
- **`kubectl get nodes` shows only one** → workers couldn't reach the API
  server. Open `6443/tcp` in the security group **within the SG**, not just
  from your laptop.
- **Flannel pod CrashLoopBackOff** → swap is back on. Run
  `sudo swapoff -a` on the affected node.
