#!/usr/bin/env bash
# Smoke test for the gateway-fronted microservice stack.
# Validates the assignment's 9 scenarios against a running gateway.
# Usage:
#   ./scripts/smoke-test.sh                          # docker-compose on localhost:4000
#   BASE=http://<EC2-1>:30000 ./scripts/smoke-test.sh  # K8s on EC2
set -u
BASE="${BASE:-http://localhost:4000}"
PASS=0
FAIL=0

ts=$(date +%s)
STUDENT_EMAIL="student-${ts}@test.local"
TEACHER_EMAIL="teacher-${ts}@test.local"
PASSWORD="Passw0rd1"

step() { printf "\n\033[1;36m== %s ==\033[0m\n" "$1"; }
ok()   { PASS=$((PASS+1)); printf "  \033[1;32mPASS\033[0m %s\n" "$1"; }
bad()  { FAIL=$((FAIL+1)); printf "  \033[1;31mFAIL\033[0m %s\n  body=%s\n" "$1" "$2"; }

# call METHOD PATH EXPECTED_STATUS [BODY] [TOKEN]
call() {
  local method=$1 path=$2 expected=$3 body=${4:-} token=${5:-}
  local args=(-sS -o /tmp/sm.body -w "%{http_code}" -X "$method" "$BASE$path")
  if [[ -n "$body"  ]]; then args+=(-H "Content-Type: application/json" --data "$body"); fi
  if [[ -n "$token" ]]; then args+=(-H "Authorization: Bearer $token"); fi
  local code; code=$(curl "${args[@]}" || echo "000")
  if [[ "$code" == "$expected" ]]; then
    ok "$method $path -> $code"
  else
    bad "$method $path expected $expected got $code" "$(cat /tmp/sm.body)"
  fi
}

# Extract a field from the envelope: env_field <json> <jq-path>
# We use python to avoid jq dependency on minimal Ubuntu nodes.
env_field() {
  python3 -c "import json,sys; d=json.loads(sys.argv[1]); k=sys.argv[2].split('.');
o=d
for p in k:
    o=o.get(p) if isinstance(o,dict) else None
    if o is None: break
print(o or '')" "$1" "$2"
}

step "Gateway healthcheck"
call GET /actuator/health 200

step "Register a student"
call POST /register/student 201 "{\"email\":\"$STUDENT_EMAIL\",\"password\":\"$PASSWORD\"}"

step "Register a teacher"
call POST /register/teacher 201 "{\"email\":\"$TEACHER_EMAIL\",\"password\":\"$PASSWORD\"}"

step "Reject duplicate registration"
call POST /register/student 409 "{\"email\":\"$STUDENT_EMAIL\",\"password\":\"$PASSWORD\"}"

step "Login as student"
LOGIN_BODY=$(curl -sS -X POST "$BASE/login" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$STUDENT_EMAIL\",\"password\":\"$PASSWORD\",\"role\":\"student\"}")
STUDENT_TOKEN=$(env_field "$LOGIN_BODY" "data.accessToken")
STUDENT_REFRESH=$(env_field "$LOGIN_BODY" "data.refreshToken")
[[ -n "$STUDENT_TOKEN" ]] && ok "student access JWT acquired (${#STUDENT_TOKEN} chars)" || bad "student login" "$LOGIN_BODY"
[[ -n "$STUDENT_REFRESH" ]] && ok "student refresh JWT acquired" || bad "student login refresh" "$LOGIN_BODY"

step "Login as teacher"
LOGIN_BODY=$(curl -sS -X POST "$BASE/login" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$TEACHER_EMAIL\",\"password\":\"$PASSWORD\",\"role\":\"teacher\"}")
TEACHER_TOKEN=$(env_field "$LOGIN_BODY" "data.accessToken")
[[ -n "$TEACHER_TOKEN" ]] && ok "teacher access JWT acquired (${#TEACHER_TOKEN} chars)" || bad "teacher login" "$LOGIN_BODY"

step "Refresh student access token"
REFRESH_BODY=$(curl -sS -X POST "$BASE/refresh" \
  -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$STUDENT_REFRESH\"}")
NEW_ACCESS=$(env_field "$REFRESH_BODY" "data.accessToken")
[[ -n "$NEW_ACCESS" && "$NEW_ACCESS" != "$STUDENT_TOKEN" ]] && ok "refresh minted a NEW access token" || bad "refresh" "$REFRESH_BODY"

step "Reject refresh-as-access"
call GET /student/viewassignment 401 "" "$STUDENT_REFRESH"

step "Reject /student without token"
call GET /student/viewassignment 401

step "Reject /teacher without token"
call GET /teacher/searchstudent 401

step "/student with student JWT - DB write"
call POST /student/submitassignment 201 \
  '{"title":"Math HW 1","content":"Solve linear equations"}' "$STUDENT_TOKEN"

step "/student with student JWT - DB read (list with pagination)"
call GET /student/viewassignment 200 "" "$STUDENT_TOKEN"

step "/teacher with teacher JWT - DB write"
call POST /teacher/addassignment 201 \
  '{"title":"Algebra exam","description":"Ch 1-3","dueDate":"2026-12-31T23:59:00Z"}' "$TEACHER_TOKEN"

step "/teacher with teacher JWT - DB read (search with pagination)"
call GET /teacher/searchstudent 200 "" "$TEACHER_TOKEN"

step "/student with TEACHER JWT - 403 (will not work)"
call GET /student/viewassignment 403 "" "$TEACHER_TOKEN"

step "/teacher with STUDENT JWT - 403 (will not work)"
call GET /teacher/searchstudent 403 "" "$STUDENT_TOKEN"

printf "\n\033[1m%d passed, %d failed\033[0m\n" "$PASS" "$FAIL"
[[ $FAIL -eq 0 ]] || exit 1
