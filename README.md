# Microservices Deployment — Docker · Kubernetes · Multi-EC2 (Spring Boot)

A four-service Spring Boot stack deployed across three AWS EC2 nodes via
Kubernetes. Built for the AUPP "Design and Deployment of a Scalable
Microservices-Based Task Management System" assignment.

## Architecture

```
                  ┌──────────── EC2-1 (role=frontend, db=mongo) ────────────┐
                  │                                                         │
   Postman ───►   │   api-gateway (Spring Cloud Gateway, :4000)             │
                  │      │                                                  │
                  │      ├─► login-service (Spring Boot, :5002) ──┐         │
                  │      │                                        │         │
                  │      ▼                                        ▼         │
                  │                                          MongoDB        │
                  └──────────────────────────────────────────────│──────────┘
                         │ /student                              │
                         ▼                                       │
                  ┌──────────── EC2-2 (role=student) ────────────┤
                  │   student-service (:5000) + Vol1 (emptyDir)  │
                  └──────────────────────────────────────────────┤
                         │ /teacher                              │
                         ▼                                       │
                  ┌──────────── EC2-3 (role=teacher) ────────────┘
                  │   teacher-service (:5001) + Vol2 (emptyDir)
                  └──────────────────────────────────────────────
```

The gateway authenticates every protected request and forwards
`X-User-Email` / `X-User-Role` headers downstream — student/teacher
services trust those headers and never re-validate the JWT, so they stay
small and focused.

| Service          | Stack                      | Port | DB           | Pinned to |
| ---------------- | -------------------------- | ---- | ------------ | --------- |
| `api-gateway`    | Spring Cloud Gateway       | 4000 | —            | EC2-1     |
| `login-service`  | Spring Boot + Spring Sec   | 5002 | `auth_db`    | EC2-1     |
| `student-service`| Spring Boot                | 5000 | `student_db` | EC2-2     |
| `teacher-service`| Spring Boot                | 5001 | `teacher_db` | EC2-3     |

## Repository layout

```
.
├── api-gateway/          Spring Cloud Gateway (JWT validation + role filter)
├── login-service/        Issues JWT after credential check (BCrypt + Mongo)
├── student-service/      /submitassignment, /viewassignment, /studentupdateprofile, /studentresubmitassignment
├── teacher-service/      /addassignment, /searchstudent, /removeassignment/{id}
├── docker-compose.yml    Local stack (Mongo + 4 services)
├── k8s/                  Manifests (00..40) — apply in numeric order
├── infra/scripts/        bootstrap-ec2.sh, init-control-plane.sh, label-nodes.sh, build-and-push.sh, apply-k8s.sh
└── postman/              microservices-k8s.postman_collection.json
```

## Quickstart — local (docker-compose)

```bash
docker-compose up --build -d
docker-compose logs -f gateway
```

The login service seeds two demo users on first boot:

| Email                    | Password    | Role     |
| ------------------------ | ----------- | -------- |
| `student1@itc.edu.kh`    | `student123`| student  |
| `teacher1@itc.edu.kh`    | `teacher123`| teacher  |

```bash
# Login as student
curl -s -X POST http://localhost:4000/login \
  -H "Content-Type: application/json" \
  -d '{"email":"student1@itc.edu.kh","password":"student123","role":"student"}' \
  | tee /tmp/student.json
STUDENT=$(jq -r .token /tmp/student.json)

# Submit an assignment (DB write)
curl -s -X POST http://localhost:4000/student/submitassignment \
  -H "Authorization: Bearer $STUDENT" \
  -H "Content-Type: application/json" \
  -d '{"title":"HW1","content":"linear equations"}'

# /teacher with student JWT → 403
curl -i -X GET http://localhost:4000/teacher/searchstudent \
  -H "Authorization: Bearer $STUDENT"
```

## Quickstart — Kubernetes on three EC2s

See [`DEPLOYMENT.md`](DEPLOYMENT.md) for the full multi-EC2 walkthrough.
Short version:

```bash
# On every EC2 (worker + control plane)
sudo bash infra/scripts/bootstrap-ec2.sh

# On EC2-1 only
bash infra/scripts/init-control-plane.sh
# … then run the printed `kubeadm join` command on EC2-2 and EC2-3.

# Back on EC2-1
EC2_1=ec2-1 EC2_2=ec2-2 EC2_3=ec2-3 bash infra/scripts/label-nodes.sh
DOCKERHUB_USER=youruser bash infra/scripts/build-and-push.sh
sed -i.bak "s|DOCKERHUB_USER|youruser|g" k8s/*.yaml
bash infra/scripts/apply-k8s.sh
```

The gateway is exposed as a NodePort on `30000`, so requests land at
`http://<EC2-1 public IP>:30000`.

## API surface

| Method | Path                              | Caller    | What it does                           |
| ------ | --------------------------------- | --------- | -------------------------------------- |
| POST   | `/login`                          | anyone    | issues a JWT for valid credentials     |
| POST   | `/register`                       | anyone    | (lab) creates a new student/teacher    |
| POST   | `/student/submitassignment`       | student   | persists an assignment to Mongo        |
| GET    | `/student/viewassignment`         | student   | returns this student's assignments     |
| PUT    | `/student/studentupdateprofile`   | student   | updates the latest assignment          |
| PUT    | `/student/studentresubmitassignment` | student | re-submits the latest assignment      |
| POST   | `/teacher/addassignment`          | teacher   | persists a teacher-owned assignment    |
| GET    | `/teacher/searchstudent?title=…`  | teacher   | search the teacher's own assignments   |
| DELETE | `/teacher/removeassignment/{id}`  | teacher   | delete one of the teacher's records    |

Every protected route returns:

* **401** if no/invalid/expired Bearer token
* **403** if the token's role doesn't match the route's required role

## Tests & coverage

Every service ships with **unit tests** (Mockito + plain JUnit) and
**integration tests** (Spring Boot context, MockMvc, Flapdoodle embedded
Mongo, or `WebTestClient` for the gateway). The build fails if line
coverage drops below **80%** — enforced by JaCoCo's `check` goal.

```bash
( cd login-service   && mvn -B clean verify )
( cd student-service && mvn -B clean verify )
( cd teacher-service && mvn -B clean verify )
( cd api-gateway     && mvn -B clean verify )
```

Current results (run `mvn clean verify` to reproduce):

| Service          | Tests | Line | Branch | Gate (≥80% line) |
| ---------------- | ----: | ---: | -----: | ---------------- |
| login-service    | 30    | 97%  | 88%    | ✅                |
| student-service  | 24    | 96%  | 78%    | ✅                |
| teacher-service  | 24    | 95%  | 90%    | ✅                |
| api-gateway      | 10    | 96%  | 71%    | ✅                |
| **Total**        | **88**| ~96% |        | ✅                |

After `mvn verify` each service's HTML report is at
`<service>/target/site/jacoco/index.html`. JaCoCo excludes
`*Application.class`, the `dto/` package (records have no logic), and
exception classes from the coverage calculation.

The student/teacher integration tests boot a real in-memory Mongo via
Flapdoodle, so the first run needs network access to download the Mongo
binary.

## Configuration

Every service reads config from environment variables (or `application.yml`
defaults). The Kubernetes Secret `app-secrets` carries the production
values; for local docker-compose they are inlined in `docker-compose.yml`.

| Variable                | Used by               | Notes                                        |
| ----------------------- | --------------------- | -------------------------------------------- |
| `JWT_SECRET`            | login + gateway       | must be ≥ 32 bytes, identical on both        |
| `JWT_EXPIRATION_SECONDS`| login                 | default 86400                                |
| `MONGO_URI`             | login/student/teacher | Mongo connection string                      |
| `LOGIN_SERVICE_URL`     | gateway               | upstream URL for `/login`, `/register`       |
| `STUDENT_SERVICE_URL`   | gateway               | upstream URL for `/student/**`               |
| `TEACHER_SERVICE_URL`   | gateway               | upstream URL for `/teacher/**`               |
| `SEED_USERS`            | login                 | set `false` in prod, `true` for the demo     |

## License

This is course work — use however you'd like.
