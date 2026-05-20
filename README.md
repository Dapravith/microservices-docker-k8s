# AUPP Microservices — Docker · Kubernetes · 3× EC2

A four-service Spring Boot stack that demonstrates JWT login, role-based
authorization at the API gateway, and per-domain MongoDB persistence —
deployed across three AWS EC2 instances orchestrated by Kubernetes.

> Assignment: *Design and Deployment of a Scalable Microservices-Based Task
> Management System Using Docker and Kubernetes on AWS EC2.*

## Architecture

```
                        EC2-1  (role=frontend)            EC2-2 (role=student)        EC2-3 (role=teacher)
                        ┌────────────────────────────┐    ┌──────────────────────┐    ┌──────────────────────┐
   Postman / curl ───►  │  api-gateway   :30080 NP   │    │  student-service     │    │  teacher-service     │
   /auth/login          │  + JWT filter / RBAC       │    │       :8082          │    │       :8083          │
                        │                            │    │                      │    │                      │
                        │  auth-service  :8081       │    │   PVC Vol1 (1 GiB)   │    │   PVC Vol2 (1 GiB)   │
                        │                            │    └──────────┬───────────┘    └──────────┬───────────┘
                        │  mongodb       :27017      │               │                           │
                        │  (PVC, auth_db)            │               │ student_db                │ teacher_db
                        └─────────────┬──────────────┘               │                           │
                                      └───────────────────────────────┴───────────────────────────┘
                                                  ClusterIP DNS:  mongodb.aupp.svc.cluster.local
```

| Service           | Port | Pinned to   | Responsibility |
| ----------------- | ---- | ----------- | -------------- |
| `api-gateway`     | 8080 (NodePort 30080) | EC2-1 (`role=frontend`) | Edge routing + JWT validation + role enforcement |
| `auth-service`    | 8081 | EC2-1 (`role=frontend`) | `/auth/register`, `/auth/login` (issues JWTs)    |
| `student-service` | 8082 | EC2-2 (`role=student`)  | `/student/**` — only callable with `STUDENT` JWT |
| `teacher-service` | 8083 | EC2-3 (`role=teacher`)  | `/teacher/**` — only callable with `TEACHER` JWT |
| `mongodb`         | 27017| EC2-1                   | Single Mongo instance, three databases           |

The gateway is the **only** service that understands JWTs. After it validates a
token it rewrites the request with `X-User-Email` / `X-User-Role` headers,
which the downstream services trust. That keeps the domain services tiny.

## Repository layout

```
.
├── api-gateway/        Spring Cloud Gateway — JWT validation + role filter
├── auth-service/       Spring Boot — register + login, issues JWTs
├── student-service/    Spring Boot — student CRUD (student_db)
├── teacher-service/    Spring Boot — teacher CRUD (teacher_db)
├── docker-compose.yml  Local single-host stack (Mongo + 4 services)
├── k8s/
│   ├── 00-namespace.yaml
│   ├── 01-secrets.yaml          JWT + Mongo creds (rotate before deploy!)
│   ├── 10-mongodb.yaml          PVC + Deployment + Service
│   ├── 20-auth-service.yaml
│   ├── 30-student-service.yaml  + PVC Vol1
│   ├── 40-teacher-service.yaml  + PVC Vol2
│   └── 50-api-gateway.yaml      NodePort 30080
├── infra/scripts/      bootstrap-ec2, init-control-plane, label-nodes,
│                       build-and-push, apply-k8s, seed-users, test-all
├── postman/            Postman collection — covers all 9 demo cases
├── docs/               Project report + architecture diagram
├── DEPLOYMENT.md       AWS step-by-step runbook
└── README.md           (this file)
```

## Quickstart — local docker-compose

```bash
docker compose up --build      # builds & starts Mongo + 4 services
bash infra/scripts/seed-users.sh
bash infra/scripts/test-all.sh
```

Expected output of `test-all.sh`:

```
==> 1) /student/me with STUDENT JWT (expect 200)
HTTP/1.1 200 OK
{"service":"student-service","email":"student1@aupp.edu","role":"STUDENT"}

==> 2) /teacher/me with TEACHER JWT (expect 200)
HTTP/1.1 200 OK
{"service":"teacher-service","email":"teacher1@aupp.edu","role":"TEACHER"}

==> 3) /student/me with TEACHER JWT (expect 403)
HTTP/1.1 403 Forbidden
{"error":"Forbidden","message":"role 'TEACHER' is not permitted to access /student/me"}

==> 4) /teacher/me with STUDENT JWT (expect 403)
HTTP/1.1 403 Forbidden
{"error":"Forbidden","message":"role 'STUDENT' is not permitted to access /teacher/me"}

==> 5) /student/me with no JWT (expect 401)
HTTP/1.1 401 Unauthorized
{"error":"Unauthorized","message":"missing Bearer token"}
```

## Quickstart — AWS (3 × EC2 + Kubernetes)

See **[DEPLOYMENT.md](DEPLOYMENT.md)** for the full runbook. The TL;DR:

```bash
# On all 3 EC2 nodes
sudo bash infra/scripts/bootstrap-ec2.sh

# On EC2-1 only
sudo bash infra/scripts/init-control-plane.sh
# (copy the kubeadm join command into EC2-2 and EC2-3)

EC2_1=aupp-ec2-1 EC2_2=aupp-ec2-2 EC2_3=aupp-ec2-3 \
  bash infra/scripts/label-nodes.sh

REGISTRY=docker.io/<you> TAG=1.0.0 bash infra/scripts/build-and-push.sh
IMAGE_REPO=docker.io/<you>          bash infra/scripts/apply-k8s.sh
```

The gateway is then reachable at `http://<any-EC2-public-IP>:30080`.

## API surface

### Auth (public)

```
POST /auth/register     { email, password, role: "STUDENT"|"TEACHER", fullName }
POST /auth/login        { email, password }   → { token, email, role, expiresInSeconds }
```

### Student (requires `role=STUDENT`)

```
GET    /student/me        whoami test endpoint
GET    /student           list students owned by the caller
POST   /student           { name, major, year, gpa }
GET    /student/{id}
PUT    /student/{id}
DELETE /student/{id}
```

### Teacher (requires `role=TEACHER`)

```
GET    /teacher/me
GET    /teacher
POST   /teacher           { name, department, courses[], yearsOfExperience }
GET    /teacher/{id}
PUT    /teacher/{id}
DELETE /teacher/{id}
```

The gateway returns:
- `401 Unauthorized` when no Bearer token is supplied to a protected route
- `403 Forbidden` when the JWT's `role` claim doesn't match the path

## Configuration reference

| Variable               | Default                                  | Used by         |
| ---------------------- | ---------------------------------------- | --------------- |
| `MONGO_URI`            | `mongodb://mongodb:27017/<service_db>`   | all services    |
| `JWT_SECRET`           | dev placeholder                          | gateway + auth  |
| `JWT_TTL_SECONDS`      | `3600`                                   | auth-service    |
| `AUTH_SERVICE_URI`     | `http://auth-service:8081`               | gateway         |
| `STUDENT_SERVICE_URI`  | `http://student-service:8082`            | gateway         |
| `TEACHER_SERVICE_URI`  | `http://teacher-service:8083`            | gateway         |

`JWT_SECRET` **must be identical** between `api-gateway` and `auth-service` —
the gateway uses it to verify signatures. In Kubernetes both pods read it
from the `app-secrets` Secret.

## License

Academic use — AUPP coursework.
