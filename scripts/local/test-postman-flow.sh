#!/usr/bin/env bash
set -euo pipefail

GATEWAY="${GATEWAY:-http://localhost:30080}"
PYTHON_BIN="${PYTHON_BIN:-python3}"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

json_value() {
  "$PYTHON_BIN" -c 'import json,sys; print(json.load(sys.stdin)[sys.argv[1]])' "$1"
}

status_request() {
  local output_file="$1"
  shift
  curl -sS -o "$output_file" -w "%{http_code}" "$@"
}

echo "Gateway: $GATEWAY"

echo "Registering demo users"
status_request "$TMP_DIR/register-student.json" \
  -X POST "$GATEWAY/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"email":"student1@aupp.edu","password":"student123","role":"STUDENT","fullName":"Student One"}' >/dev/null || true

status_request "$TMP_DIR/register-teacher.json" \
  -X POST "$GATEWAY/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"email":"teacher1@aupp.edu","password":"teacher123","role":"TEACHER","fullName":"Teacher One"}' >/dev/null || true

STUDENT_LOGIN="$(curl -sS -X POST "$GATEWAY/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"student1@aupp.edu","password":"student123"}')"
TEACHER_LOGIN="$(curl -sS -X POST "$GATEWAY/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"teacher1@aupp.edu","password":"teacher123"}')"

STUDENT_TOKEN="$(printf '%s' "$STUDENT_LOGIN" | json_value token)"
TEACHER_TOKEN="$(printf '%s' "$TEACHER_LOGIN" | json_value token)"

DUE_DATE="$("$PYTHON_BIN" -c 'from datetime import date, timedelta; print(date.today() + timedelta(days=7))')"

echo
echo "1) Login returned JWT tokens"
printf '%s\n' "$STUDENT_LOGIN"
printf '%s\n' "$TEACHER_LOGIN"

echo
echo "2) Teacher creates task in MongoDB through /teacher"
TASK_JSON="$(curl -sS -X POST "$GATEWAY/teacher" \
  -H "Authorization: Bearer $TEACHER_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"title\":\"Kubernetes Local Deployment\",\"description\":\"Deploy all microservices into local Kubernetes and capture screenshots.\",\"course\":\"Cloud Computing\",\"dueDate\":\"$DUE_DATE\",\"maxScore\":100}")"
printf '%s\n' "$TASK_JSON"
TASK_ID="$(printf '%s' "$TASK_JSON" | json_value id)"

echo
echo "3) Teacher reads own MongoDB tasks through /teacher"
curl -sS "$GATEWAY/teacher" -H "Authorization: Bearer $TEACHER_TOKEN"
echo

echo
echo "4) Student reads teacher tasks through /student/tasks"
curl -sS "$GATEWAY/student/tasks" -H "Authorization: Bearer $STUDENT_TOKEN"
echo

echo
echo "5) Student creates submission in MongoDB through /student"
curl -sS -X POST "$GATEWAY/student" \
  -H "Authorization: Bearer $STUDENT_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"taskId\":\"$TASK_ID\",\"answer\":\"Local Kubernetes deployment is working with MongoDB and JWT authorization.\"}"
echo

echo
echo "6) Student reads own MongoDB submissions through /student"
curl -sS "$GATEWAY/student" -H "Authorization: Bearer $STUDENT_TOKEN"
echo

TEACHER_TO_STUDENT_CODE="$(status_request "$TMP_DIR/teacher-to-student.json" "$GATEWAY/student" -H "Authorization: Bearer $TEACHER_TOKEN")"
STUDENT_TO_TEACHER_CODE="$(status_request "$TMP_DIR/student-to-teacher.json" "$GATEWAY/teacher" -H "Authorization: Bearer $STUDENT_TOKEN")"

echo
echo "7) Teacher JWT to /student returns $TEACHER_TO_STUDENT_CODE"
cat "$TMP_DIR/teacher-to-student.json"
echo
echo "8) Student JWT to /teacher returns $STUDENT_TO_TEACHER_CODE"
cat "$TMP_DIR/student-to-teacher.json"
echo

if [[ "$TEACHER_TO_STUDENT_CODE" != "403" || "$STUDENT_TO_TEACHER_CODE" != "403" ]]; then
  echo "Expected both cross-role checks to return 403" >&2
  exit 1
fi

echo
echo "Local Postman-equivalent flow passed."
