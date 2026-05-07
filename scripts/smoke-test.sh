#!/usr/bin/env bash
# Smoke test for the gateway-fronted microservice stack.
# Usage:  ./scripts/smoke-test.sh   (assumes docker compose stack is up on :4000)

set -u
BASE="${BASE:-http://localhost:4000}"
PASS=0
FAIL=0

ts=$(date +%s)
STUDENT_EMAIL="student-${ts}@test.local"
TEACHER_EMAIL="teacher-${ts}@test.local"
PASSWORD="Passw0rd!"

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

step "Gateway healthcheck"
call GET /actuator/health 200

step "Register a student"
call POST /register/student 201 "{\"email\":\"$STUDENT_EMAIL\",\"password\":\"$PASSWORD\"}"

step "Register a teacher"
call POST /register/teacher 201 "{\"email\":\"$TEACHER_EMAIL\",\"password\":\"$PASSWORD\"}"

step "Reject duplicate registration"
call POST /register/student 409 "{\"email\":\"$STUDENT_EMAIL\",\"password\":\"$PASSWORD\"}"

step "Login as student"
STUDENT_TOKEN=$(curl -sS -X POST "$BASE/login" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$STUDENT_EMAIL\",\"password\":\"$PASSWORD\",\"role\":\"student\"}" \
  | sed -n 's/.*"token"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')
[[ -n "$STUDENT_TOKEN" ]] && ok "student JWT acquired (${#STUDENT_TOKEN} chars)" || bad "student login" "no token"

step "Login as teacher"
TEACHER_TOKEN=$(curl -sS -X POST "$BASE/login" \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$TEACHER_EMAIL\",\"password\":\"$PASSWORD\",\"role\":\"teacher\"}" \
  | sed -n 's/.*"token"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')
[[ -n "$TEACHER_TOKEN" ]] && ok "teacher JWT acquired (${#TEACHER_TOKEN} chars)" || bad "teacher login" "no token"

step "Reject /student without token"
call GET /student/viewassignment 401

step "Reject /teacher without token"
call GET /teacher/searchstudent 401

step "Reject /student with teacher token (wrong role)"
call GET /student/viewassignment 403 "" "$TEACHER_TOKEN"

step "Allow /student with student token"
call GET /student/viewassignment 200 "" "$STUDENT_TOKEN"

step "Allow /teacher with teacher token"
call GET /teacher/searchstudent 200 "" "$TEACHER_TOKEN"

printf "\n\033[1m%d passed, %d failed\033[0m\n" "$PASS" "$FAIL"
[[ $FAIL -eq 0 ]] || exit 1
