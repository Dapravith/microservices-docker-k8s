# Deployment Runbook

This runbook keeps the workflow local-first:

1. Prove the system in local Kubernetes.
2. Reuse the same manifests on 3 EC2 instances.
3. Confirm all Docker images run inside Kubernetes pods.

## 1. Local Kubernetes

Requirements: Docker, `kind`, `kubectl`, Java 21+ (Maven is bundled via `./mvnw`).

Run everything from the repo root — two scripts, in order:

**Step 1 — deploy.** One script does the whole pipeline:

```bash
./scripts/local/deploy.sh
```

It runs, in order:
1. `create-kind-cluster.sh` — creates the 3-node kind cluster and labels nodes `admin` / `student` / `teacher`.
2. `build-images.sh` — builds all 4 service images.
3. `kind load` — loads the images into the cluster.
4. `kubectl apply -k deploy/k8s` + `rollout restart` — applies manifests and forces pods onto the freshly built images.

You do **not** run `create-kind-cluster.sh` or `build-images.sh` yourself — `deploy.sh` calls them.

**Step 2 — verify:**

```bash
./scripts/test-postman-flow.sh
```

Defaults to `http://localhost:30080`; no env var needed.

The local kind cluster has 3 nodes:

```bash
kubectl get nodes --show-labels
kubectl -n aupp get deploy,sts,svc,pvc -o wide
kubectl -n aupp get pods -o wide
```

Expected placement:

| Local node | Label | Pods |
| --- | --- | --- |
| `aupp-local-control-plane` | `role=admin` | `api-gateway`, `login-service`, `mongodb` |
| `aupp-local-worker` | `role=student` | `student-service` |
| `aupp-local-worker2` | `role=teacher` | `teacher-service` |

Use one local gateway URL in Postman. The `api-gateway` is a NodePort and the
`kind` cluster maps it to the host — no port-forward needed:

```text
http://localhost:30080
```

Do not point Postman directly at `login-service`, `student-service`, or
`teacher-service`; all API testing should go through the gateway URL above.

## 2. EC2 Kubernetes

Unlike local, EC2 is staged across 3 machines, so the scripts run in this order:

1. **Bootstrap** (Section 3) — `bootstrap-k3s-server.sh` on `ec2_1`, then `bootstrap-k3s-agent.sh` on `ec2_2` and `ec2_3`.
2. **Build images** (Section 4) — `build-node-images.sh` on each node with its `NODE_ROLE`.
3. **Deploy** (Section 5) — `deploy.sh` on `ec2_1` only.
4. **Verify** (Section 6) — `test-postman-flow.sh` against the gateway.

Order matters: agents must join before you build/deploy, and all images must exist before `deploy.sh` (pods use `imagePullPolicy: IfNotPresent` with no registry, so a missing image means `ImagePullBackOff`).

Create 3 Ubuntu EC2 instances. Use one security group that allows:

- SSH `22/tcp` from your IP.
- Kubernetes API `6443/tcp` between the EC2 instances.
- Kubelet `10250/tcp` between the EC2 instances.
- NodePort `30080/tcp` from your IP.

Recommended names:

| EC2 | Kubernetes node name | Label | Pods |
| --- | --- | --- | --- |
| `ec2_1` | `ec2-1` | `role=admin` | `api-gateway`, `login-service`, `mongodb` |
| `ec2_2` | `ec2-2` | `role=student` | `student-service` |
| `ec2_3` | `ec2-3` | `role=teacher` | `teacher-service` |

## 3. Bootstrap EC2 Nodes

Copy this project to all 3 EC2 instances.

On `ec2_1`:

```bash
cd microservices-docker-k8s
NODE_NAME=ec2-1 ./scripts/ec2/bootstrap-k3s-server.sh
```

The script prints the K3s token and server URL.

On `ec2_2`:

```bash
cd microservices-docker-k8s
K3S_URL=https://<ec2-1-private-ip>:6443 \
K3S_TOKEN=<token-from-ec2-1> \
NODE_NAME=ec2-2 \
  ./scripts/ec2/bootstrap-k3s-agent.sh
```

On `ec2_3`:

```bash
cd microservices-docker-k8s
K3S_URL=https://<ec2-1-private-ip>:6443 \
K3S_TOKEN=<token-from-ec2-1> \
NODE_NAME=ec2-3 \
  ./scripts/ec2/bootstrap-k3s-agent.sh
```

Back on `ec2_1`:

```bash
kubectl get nodes -o wide
```

## 4. Build Only The Images Each EC2 Needs

The default image namespace is `dapravith99`.

Optional Docker Hub path from your laptop/local machine:

```bash
docker login
./scripts/local/build-images.sh
./scripts/local/push-images.sh
```

The EC2 steps below still build/import only the images needed on each node, which keeps the assignment placement clear.

On `ec2_1`:

```bash
NODE_ROLE=admin ./scripts/ec2/build-node-images.sh
```

On `ec2_2`:

```bash
NODE_ROLE=student ./scripts/ec2/build-node-images.sh
```

On `ec2_3`:

```bash
NODE_ROLE=teacher ./scripts/ec2/build-node-images.sh
```

This keeps the app images aligned with the assignment:

- `ec2_1`: API Gateway image and Login image.
- `ec2_2`: Student image only.
- `ec2_3`: Teacher image only.

MongoDB uses the public `mongo:7` image as an infrastructure pod on `ec2_1`.

## 5. Deploy On EC2

Run from `ec2_1` only:

```bash
EC2_1_NODE=ec2-1 EC2_2_NODE=ec2-2 EC2_3_NODE=ec2-3 ./scripts/ec2/deploy.sh
```

This labels the nodes (`label-nodes.sh` runs inside `deploy.sh` — no need to run
it yourself), applies the manifests, and runs `rollout restart` so the pods pick
up the images you just built in Section 4.

Verify:

```bash
kubectl -n aupp get deploy,sts,svc,pvc -o wide
kubectl -n aupp get pods -o wide
```

Gateway URL:

```text
http://<ec2-1-public-ip>:30080
```

## 6. Postman Test

Quick CLI check (same script used for local — just point it at the EC2 gateway):

```bash
GATEWAY=http://<ec2-1-public-ip>:30080 ./scripts/test-postman-flow.sh
```

Import:

```text
postman/microservices-k8s.postman_collection.json
postman/task07-local.postman_environment.json
```

Set collection variable:

```text
gateway = http://<ec2-1-public-ip>:30080
```

Run the requests in order. Capture the assignment screenshots for JWT login, allowed student/teacher access, and forbidden cross-role access.

## Cleanup

Local:

```bash
./scripts/local/delete-kind-cluster.sh
```

EC2:

```bash
kubectl delete namespace aupp
```

Then terminate the EC2 instances when the assignment evidence is captured.
