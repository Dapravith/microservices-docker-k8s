# AUPP Local-First Kubernetes Microservices

Spring Boot microservices project for the assignment:

> Design and Deployment of a Scalable Microservices-Based Task Management System Using Docker and Kubernetes on AWS EC2.

The project runs in local Kubernetes first, then uses the same manifests on 3 EC2 instances with strict node placement.

## Architecture

```text
Laptop / Postman
      |
      v
api-gateway pod :30080
      |
      +--> login-service pod      role=admin
      +--> teacher-service pod    role=teacher
      +--> student-service pod    role=student

mongodb StatefulSet pod runs on role=admin
```

## EC2 / Local Node Placement

| Node | Label | Pods |
| --- | --- | --- |
| `ec2_1` / local control-plane | `role=admin` | `api-gateway`, `login-service`, `mongodb` |
| `ec2_2` / local worker 1 | `role=student` | `student-service` |
| `ec2_3` / local worker 2 | `role=teacher` | `teacher-service` |

No `role=frontend` label is used.

## Project Structure

```text
.
├── services/
│   ├── api-gateway/
│   ├── login-service/
│   ├── student-service/
│   └── teacher-service/
├── deploy/k8s/
│   ├── kustomization.yaml
│   ├── kind-cluster.yaml
│   └── *.yaml
├── scripts/
│   ├── local/
│   └── ec2/
├── postman/
└── docs/
```

## API

### Public Auth

```http
POST /auth/register
POST /auth/login
```

### Teacher JWT Required

```http
GET  /teacher/me
POST /teacher
GET  /teacher
POST /teacher/tasks
GET  /teacher/tasks
```

### Student JWT Required

```http
GET  /student/me
GET  /student/tasks
POST /student
GET  /student
POST /student/submissions
GET  /student/submissions
```

The gateway validates JWTs and injects `X-User-Email`, `X-User-Role`, and `X-User-Full-Name` headers into downstream requests.

## Local Kubernetes Quickstart

Requirements: Docker, `kind`, `kubectl`, Maven, Java 21+.

```bash
./scripts/local/deploy.sh
./scripts/local/test-postman-flow.sh
```

Docker image names default to your Docker Hub namespace:

```text
dapravith99/api-gateway:1.0.0
dapravith99/login-service:1.0.0
dapravith99/student-service:1.0.0
dapravith99/teacher-service:1.0.0
```

Optional Docker Hub push after `docker login`:

```bash
./scripts/local/push-images.sh
```

Gateway URL:

```text
http://localhost:30080
```

Import the Postman collection:

```text
postman/microservices-k8s.postman_collection.json
```

## Screenshot Checklist

1. 3 EC2 instances running.
2. Docker images on the EC2 nodes.
3. Kubernetes services and deployments.
4. Pods spread across the 3 EC2 nodes.
5. Postman login returns JWT.
6. `/student` with student JWT works and reads/writes MongoDB data.
7. `/teacher` with teacher JWT works and reads/writes MongoDB data.
8. `/student` with teacher JWT returns `403`.
9. `/teacher` with student JWT returns `403`.

See [DEPLOYMENT.md](DEPLOYMENT.md) for the full EC2 runbook.
