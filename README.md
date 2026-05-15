# Microservices Deployment — Docker · Kubernetes · Multi-EC2 (Spring Boot)

A five-service Spring Boot stack deployed across three AWS EC2 nodes via
Kubernetes. Built for the AUPP "Design and Deployment of a Scalable
Microservices-Based Task Management System" assignment.

## Architecture

```
                  ┌──────────── EC2-1 (role=frontend, db=mongo) ─────────────┐
                  │                                                          │
   Postman ───►   │   api-gateway (Spring Cloud Gateway, :4000)              │
                  │      │                                                   │
                  │      ├─► registration         (Spring Boot, :5003) ─┐   │
                  │      ├─► authentication-service (Spring Boot, :5002) ┤   │
                  │      │                                              ▼   │
                  │      │                                          MongoDB  │
                  └──────│──────────────────────────────────────────────│───┘
                         │ /student                                     │
                         ▼                                              │
                  ┌──────────── EC2-2 (role=student) ────────────┐      │
                  │   student-service (:5000) + Vol1 (emptyDir)  │──────┤
                  └──────────────────────────────────────────────┘      │
                         │ /teacher                                     │
                         ▼                                              │
                  ┌──────────── EC2-3 (role=teacher) ────────────┐      │
                  │   teacher-service (:5001) + Vol2 (emptyDir)  │──────┘
                  └──────────────────────────────────────────────┘
```

The gateway authenticates every protected request and forwards
`X-User-Email` / `X-User-Role` headers downstream — student/teacher
services trust those headers and never re-validate the JWT, so they stay
small and focused. Splitting registration into its own service keeps the
login service single-purpose: it never writes to the users collection.

| Service                  | Stack                      | Port | DB           | Pinned to | Responsibility |
| ------------------------ | -------------------------- | ---- | ------------ | --------- | -------------- |
| `api-gateway`            | Spring Cloud Gateway       | 4000 | —            | EC2-1     | Edge routing + JWT validation + role enforcement |
| `registration`           | Spring Boot + Spring Sec   | 5003 | `auth_db`    | EC2-1     | **Sole writer** of users collection            |
| `authentication-service` | Spring Boot + Spring Sec   | 5002 | `auth_db`    | EC2-1     | Read-only auth: validate creds → issue JWT     |
| `student-service`        | Spring Boot                | 5000 | `student_db` | EC2-2     | Student assignment domain                       |
| `teacher-service`        | Spring Boot                | 5001 | `teacher_db` | EC2-3     | Teacher assignment domain                       |

## Repository layout

```
.
├── APIGateway_Microservice/      Spring Cloud Gateway (JWT validation + role filter)
├── Registration_Microservice/    Creates users (sole writer of auth_db.users)
├── Authentication_Microservice/  Authenticates users, issues JWT (sole reader for login)
├── Student_Microservice/         Student assignment APIs
├── Teacher_Microservice/         Teacher assignment APIs
├── docker-compose.yml       Local stack (Mongo + 5 services)
├── docker-compose.sonar.yml Local SonarQube + Postgres
├── k8s/                     Manifests — one file per Deployment / Service
│                            (auth.yaml + auth-service.yaml, etc.); applied
│                            in dependency order by infra/scripts/apply-k8s.sh
├── infra/scripts/           bootstrap-ec2, init-control-plane, label-nodes,
│                            build-and-push, apply-k8s, test-all, sonar-scan
└── postman/                 Postman collection (covers every assignment screenshot)
```

## Quickstart — local (docker-compose)

```bash
docker-compose up --build -d
docker-compose logs -f gateway
```

Register a student, log in, and exercise the API:

```bash
# 1. Register
curl -s -X POST http://localhost:4000/register/student \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@itc.edu.kh","password":"secret123"}'

# 2. Login → grab token
STUDENT=$(curl -s -X POST http://localhost:4000/login \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@itc.edu.kh","password":"secret123","role":"student"}' \
  | jq -r .token)

# 3. Submit an assignment (DB write)
curl -s -X POST http://localhost:4000/student/submitassignment \
  -H "Authorization: Bearer $STUDENT" \
  -H "Content-Type: application/json" \
  -d '{"title":"HW1","content":"linear equations"}'

# 4. Negative case — /teacher with student JWT → 403
curl -i -X GET http://localhost:4000/teacher/searchstudent \
  -H "Authorization: Bearer $STUDENT"
```

## Quickstart — Kubernetes on three EC2s

See [`DEPLOYMENT.md`](DEPLOYMENT.md) for the full multi-EC2 walkthrough with
screenshot checkpoints. Below is the seven-step path from zero to a running
cluster:

```bash
# 1. Bootstrap every node (containerd + kubeadm + kubelet)
ssh ubuntu@ec2-1 'sudo bash infra/scripts/bootstrap-ec2.sh'
ssh ubuntu@ec2-2 'sudo bash infra/scripts/bootstrap-ec2.sh'
ssh ubuntu@ec2-3 'sudo bash infra/scripts/bootstrap-ec2.sh'

# 2. Init the control plane on EC2-1, then run the printed `kubeadm join …`
#    command on EC2-2 and EC2-3.
ssh ubuntu@ec2-1 'bash -s' < infra/scripts/init-control-plane.sh

# 3. Pre-create the Mongo hostPath dir + label the nodes
ssh ubuntu@ec2-1 'sudo mkdir -p /var/lib/mongo-data && sudo chmod 700 /var/lib/mongo-data'
EC2_1=ip-10-0-0-1 EC2_2=ip-10-0-0-2 EC2_3=ip-10-0-0-3 \
  ssh ubuntu@ec2-1 'bash -s' < infra/scripts/label-nodes.sh

# 4. Build and push the five service images
DOCKERHUB_USER=youruser bash infra/scripts/build-and-push.sh

# 5. Bake your Docker Hub user + a fresh JWT secret into the manifests
sed -i.bak "s|DOCKERHUB_USER|youruser|g" k8s/*.yaml
sed -i.bak "s|REPLACE_WITH_AT_LEAST_32_CHARACTERS_OF_RANDOM_BYTES|$(openssl rand -hex 32)|" k8s/01-secrets.yaml

# 6. Apply the 14 manifests in dependency order
bash infra/scripts/apply-k8s.sh

# 7. Verify
kubectl -n msp get deploy,svc,statefulset,pods -o wide
```

The gateway is exposed as a NodePort on `30000`, so requests land at
`http://<EC2-1 public IP>:30000`.

### `k8s/` layout

```
00-namespace.yaml          01-secrets.yaml
mongodb.yaml               mongodb-service.yaml          # StatefulSet + headless Service
auth.yaml                  auth-service.yaml             # authentication-service
registration.yaml          registration-service.yaml
student.yaml               student-service.yaml
teacher.yaml               teacher-service.yaml
api-gateway.yaml           api-gateway-service.yaml      # NodePort 30000
```

## API surface

| Method | Path                               | Caller    | Service              | Result |
| ------ | ---------------------------------- | --------- | -------------------- | ------ |
| POST   | `/register`                        | anyone    | registration-service | 201 + UserResponse · 409 if dup · 400 if invalid |
| POST   | `/register/student`                | anyone    | registration-service | 201 + UserResponse (role auto-set)               |
| POST   | `/register/teacher`                | anyone    | registration-service | 201 + UserResponse (role auto-set)               |
| POST   | `/login`                           | anyone    | authentication-service | 200 + Bearer JWT · 401 on bad creds            |
| POST   | `/student/submitassignment`        | student   | student-service      | 201 — persists assignment                        |
| GET    | `/student/viewassignment`          | student   | student-service      | 200 — returns this student's assignments         |
| PUT    | `/student/studentupdateprofile`    | student   | student-service      | 200 — patch latest assignment                    |
| PUT    | `/student/studentresubmitassignment`| student  | student-service      | 200 — re-submit latest assignment                |
| POST   | `/teacher/addassignment`           | teacher   | teacher-service      | 201 — persists teacher-owned assignment          |
| GET    | `/teacher/searchstudent?title=…`   | teacher   | teacher-service      | 200 — search this teacher's assignments          |
| DELETE | `/teacher/removeassignment/{id}`   | teacher   | teacher-service      | 200 — owner-scoped delete                        |

Every protected route returns:

* **401** if no/invalid/expired Bearer token
* **403** if the token's role doesn't match the route's required role

```bash
# All five services in one go:
./infra/scripts/test-all.sh

# Or individually:
( cd Registration_Microservice    && mvn -B clean verify )
( cd Authentication_Microservice  && mvn -B clean verify )
( cd Student_Microservice         && mvn -B clean verify )
( cd Teacher_Microservice         && mvn -B clean verify )
( cd APIGateway_Microservice      && mvn -B clean verify )
```

After `mvn verify` each service's HTML report is at
`<service>/target/site/jacoco/index.html`.


## Configuration

Every service reads config from environment variables (or `application.yml`
defaults). The Kubernetes Secret `app-secrets` carries the production
values; for local docker-compose they are inlined in `docker-compose.yml`.

| Variable                   | Used by                         | Notes                                  |
| -------------------------- | ------------------------------- | -------------------------------------- |
| `JWT_SECRET`               | login + gateway                 | must be ≥ 32 bytes, identical on both  |
| `JWT_EXPIRATION_SECONDS`   | login                           | default 86400                          |
| `MONGO_URI`                | login/registration/student/teacher | per-service Mongo connection string |
| `LOGIN_SERVICE_URL`        | gateway                         | upstream URL for `/login`              |
| `REGISTRATION_SERVICE_URL` | gateway                         | upstream URL for `/register/**`        |
| `STUDENT_SERVICE_URL`      | gateway                         | upstream URL for `/student/**`         |
| `TEACHER_SERVICE_URL`      | gateway                         | upstream URL for `/teacher/**`         |

## License

This is course work — use however you'd like.
