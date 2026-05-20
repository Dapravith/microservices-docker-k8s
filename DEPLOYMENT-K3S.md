# AWS Deployment Runbook — k3s on t3.micro

This is the lightweight path for AWS Academy Learner Lab (t3.micro instances,
1 GiB RAM each). It uses **k3s** instead of full kubeadm so the control plane
fits in 200 MB. Steps map 1-to-1 to `DEPLOYMENT.md` but with smaller-footprint
commands.

## Topology

| EC2  | Role | k3s role | Pods |
|------|------|----------|------|
| EC2-1 | control plane | `server` (`role=frontend`) | api-gateway, auth-service, mongodb |
| EC2-2 | worker | `agent` (`role=student`)  | student-service + Vol1 |
| EC2-3 | worker | `agent` (`role=teacher`)  | teacher-service + Vol2 |

## Step 1 — Get the project onto each EC2

From your **laptop**, copy the folder to all 3 instances. The simplest path:

```bash
KEY=~/.ssh/aupp-k8s.pem    # adjust to your key
for IP in <EC2-1-public-IP> <EC2-2-public-IP> <EC2-3-public-IP>; do
  rsync -avz --exclude archive --exclude .git -e "ssh -i $KEY" \
    ./microservices-docker-k8s/ ubuntu@$IP:~/aupp/
done
```

If your Learner Lab gives you `ec2-user` instead of `ubuntu`, substitute it.

## Step 2 — Install k3s on EC2-1 (control plane)

```bash
ssh -i $KEY ubuntu@<EC2-1-public-IP>
cd ~/aupp
bash infra/scripts/bootstrap-k3s-server.sh
```

The script prints a join command at the end — **copy it** (you'll paste it on
EC2-2 and EC2-3 next).

## Step 3 — Join EC2-2 and EC2-3 as workers

On EC2-2:

```bash
ssh -i $KEY ubuntu@<EC2-2-public-IP>
cd ~/aupp
K3S_URL=https://<EC2-1-private-IP>:6443 \
K3S_TOKEN=<token from step 2> \
NODE_NAME=ec2-2 \
  bash infra/scripts/bootstrap-k3s-agent.sh
```

On EC2-3 — same thing but `NODE_NAME=ec2-3`.

Back on EC2-1, confirm:

```bash
kubectl get nodes -o wide
# expected: ec2-1 (Ready), ec2-2 (Ready), ec2-3 (Ready)
```

## Step 4 — Label nodes

On EC2-1:

```bash
EC2_1=ec2-1 EC2_2=ec2-2 EC2_3=ec2-3 \
  bash infra/scripts/label-nodes.sh
```

## Step 5 — Build images and ship them to each node

On EC2-1:

```bash
EC2_2_IP=<EC2-2-private-IP> \
EC2_3_IP=<EC2-3-private-IP> \
  bash infra/scripts/distribute-images.sh
```

This builds all 4 images on EC2-1, imports them into the local k3s
containerd, then SSH-pipes the student image to EC2-2 and the teacher image to
EC2-3.

Note: the first build downloads Maven dependencies and takes ~5 minutes per
service on a t3.micro. Total ~20–25 minutes. Be patient.

➡️  **SCREENSHOT 2:** on each EC2, run `sudo k3s ctr images ls | grep aupp` and
screenshot the output.

## Step 6 — Rotate the JWT secret, then apply manifests

```bash
# On EC2-1, in ~/aupp
openssl rand -base64 64 | tr -d '\n' > /tmp/jwt
sed -i "s|replace-me-with-a-64-byte-random.*|$(cat /tmp/jwt)|" k8s/01-secrets.yaml

bash infra/scripts/apply-k8s.sh
```

➡️  **SCREENSHOT 3:** `kubectl -n aupp get deploy,svc -o wide`

➡️  **SCREENSHOT 4:** `kubectl -n aupp get pods -o wide`

## Step 7 — Seed users and test

```bash
GATEWAY=http://<EC2-1-public-IP>:30080 \
  bash infra/scripts/seed-users.sh

GATEWAY=http://<EC2-1-public-IP>:30080 \
  bash infra/scripts/test-all.sh
```

In Postman, import `postman/microservices-k8s.postman_collection.json` and set
the `gateway` variable to `http://<EC2-1-public-IP>:30080`. Run the requests
to capture SCREENSHOTS 5–9.

## Troubleshooting

- **Pod `OOMKilled`** — JVM heap exceeded the container limit. The Dockerfiles
  already set `-Xmx256m`; double-check with `kubectl describe pod <name>`.
- **`kubectl get nodes` shows only 1 node** — workers couldn't reach 6443.
  Confirm the security group allows 6443/tcp from itself.
- **`ImagePullBackOff`** — student/teacher image wasn't shipped to that node.
  Re-run `distribute-images.sh`.
- **First Spring Boot start takes ~90s on t3.micro** — bump `initialDelaySeconds`
  on the readiness probe if needed.

## Tear-down (return Learner Lab credits)

```bash
# In the AWS console — easy: terminate the 3 instances
# OR with AWS CLI:
aws ec2 terminate-instances --instance-ids i-... i-... i-...
```

Then **End Lab** in the AWS Academy session.
