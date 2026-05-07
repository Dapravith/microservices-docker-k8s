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
                  │      ├─► registration-service (Spring Boot, :5003) ─┐   │
                  │      ├─► login-service        (Spring Boot, :5002) ─┤   │
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

| Service               | Stack                      | Port | DB           | Pinned to | Responsibility |
| --------------------- | -------------------------- | ---- | ------------ | --------- | -------------- |
| `api-gateway`         | Spring Cloud Gateway       | 4000 | —            | EC2-1     | Edge routing + JWT validation + role enforcement |
| `registration-service`| Spring Boot + Spring Sec   | 5003 | `auth_db`    | EC2-1     | **Sole writer** of users collection            |
| `login-service`       | Spring Boot + Spring Sec   | 5002 | `auth_db`    | EC2-1     | Read-only auth: validate creds → issue JWT     |
| `student-service`     | Spring Boot                | 5000 | `student_db` | EC2-2     | Student assignment domain                       |
| `teacher-service`     | Spring Boot                | 5001 | `teacher_db` | EC2-3     | Teacher assignment domain                       |

## Repository layout

```
.
├── api-gateway/             Spring Cloud Gateway (JWT validation + role filter)
├── registration-service/    Creates users (sole writer of auth_db.users)
├── login-service/           Authenticates users, issues JWT (sole reader for login)
├── student-service/         Student assignment APIs
├── teacher-service/         Teacher assignment APIs
├── docker-compose.yml       Local stack (Mongo + 5 services)
├── docker-compose.sonar.yml Local SonarQube + Postgres
├── k8s/                     Manifests (00..40) — apply in numeric order
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

| Method | Path                               | Caller    | Service              | Result |
| ------ | ---------------------------------- | --------- | -------------------- | ------ |
| POST   | `/register`                        | anyone    | registration-service | 201 + UserResponse · 409 if dup · 400 if invalid |
| POST   | `/register/student`                | anyone    | registration-service | 201 + UserResponse (role auto-set)               |
| POST   | `/register/teacher`                | anyone    | registration-service | 201 + UserResponse (role auto-set)               |
| POST   | `/login`                           | anyone    | login-service        | 200 + Bearer JWT · 401 on bad creds              |
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

## Tests & coverage

Every service ships with **unit tests** (Mockito + plain JUnit) and
**integration tests** (Spring Boot context, MockMvc, Flapdoodle embedded
Mongo, or `WebTestClient` for the gateway). The build fails if line
coverage drops below **80%** — enforced by JaCoCo's `check` goal.

```bash
# All five services in one go:
./infra/scripts/test-all.sh

# Or individually:
( cd registration-service && mvn -B clean verify )
( cd login-service        && mvn -B clean verify )
( cd student-service      && mvn -B clean verify )
( cd teacher-service      && mvn -B clean verify )
( cd api-gateway          && mvn -B clean verify )
```

After `mvn verify` each service's HTML report is at
`<service>/target/site/jacoco/index.html`.

## Code quality — SonarQube

`sonar-maven-plugin` is wired into every POM, and each service has a
`sonar-project.properties` describing its analysis scope. To run a full
scan locally:

```bash
# 1. Bring up SonarQube (Postgres-backed)
docker-compose -f docker-compose.sonar.yml up -d
open http://localhost:9000     # default login admin/admin → change password

# 2. Generate a User Token under Account → Security and export it
export SONAR_TOKEN=<token>

# 3. Scan everything
./infra/scripts/sonar-scan.sh
```

The scan script runs `mvn clean verify` (so JaCoCo XML reports are fresh)
and then `mvn sonar:sonar` per service. You'll get five projects in
SonarQube — one per microservice — each with its own coverage,
duplications, security hotspots, and code smells dashboard.

If you have a hosted instance instead, set `SONAR_HOST` accordingly:

```bash
SONAR_HOST=https://sonarcloud.io SONAR_TOKEN=… ./infra/scripts/sonar-scan.sh
```

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
