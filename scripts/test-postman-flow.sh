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
echo "2) Teacher creates task in MongoDB through /teacher/tasks"
TASK_JSON="$(curl -sS -X POST "$GATEWAY/teacher/tasks" \
  -H "Authorization: Bearer $TEACHER_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"title\":\"Kubernetes Deployment\",\"description\":\"Deploy all microservices into Kubernetes and capture screenshots.\",\"course\":\"Cloud Computing\",\"dueDate\":\"$DUE_DATE\",\"maxScore\":100}")"
printf '%s\n' "$TASK_JSON"
TASK_ID="$(printf '%s' "$TASK_JSON" | json_value id)"

echo
echo "3) Teacher reads own MongoDB tasks through /teacher/tasks"
curl -sS "$GATEWAY/teacher/tasks" -H "Authorization: Bearer $TEACHER_TOKEN"
echo

echo
echo "4) Student reads teacher tasks through /student/tasks"
curl -sS "$GATEWAY/student/tasks" -H "Authorization: Bearer $STUDENT_TOKEN"
echo

echo
echo "5) Student creates submission in MongoDB through /student/submissions"
curl -sS -X POST "$GATEWAY/student/submissions" \
  -H "Authorization: Bearer $STUDENT_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"taskId\":\"$TASK_ID\",\"answer\":\"Kubernetes deployment is working with MongoDB and JWT authorization.\"}"
echo

echo
echo "6) Student reads own MongoDB submissions through /student/submissions"
curl -sS "$GATEWAY/student/submissions" -H "Authorization: Bearer $STUDENT_TOKEN"
echo

TEACHER_TO_STUDENT_ROOT_CODE="$(status_request "$TMP_DIR/teacher-to-student-root.json" "$GATEWAY/student" -H "Authorization: Bearer $TEACHER_TOKEN")"
STUDENT_TO_TEACHER_ROOT_CODE="$(status_request "$TMP_DIR/student-to-teacher-root.json" "$GATEWAY/teacher" -H "Authorization: Bearer $STUDENT_TOKEN")"
TEACHER_TO_STUDENT_TASKS_CODE="$(status_request "$TMP_DIR/teacher-to-student-tasks.json" "$GATEWAY/student/tasks" -H "Authorization: Bearer $TEACHER_TOKEN")"
TEACHER_TO_STUDENT_SUBMISSIONS_CODE="$(status_request "$TMP_DIR/teacher-to-student-submissions.json" "$GATEWAY/student/submissions" -H "Authorization: Bearer $TEACHER_TOKEN")"
STUDENT_TO_TEACHER_READ_CODE="$(status_request "$TMP_DIR/student-to-teacher-read.json" "$GATEWAY/teacher/tasks" -H "Authorization: Bearer $STUDENT_TOKEN")"
STUDENT_TO_TEACHER_CREATE_CODE="$(status_request "$TMP_DIR/student-to-teacher-create.json" \
  -X POST "$GATEWAY/teacher/tasks" \
  -H "Authorization: Bearer $STUDENT_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"title\":\"Forbidden Task\",\"description\":\"Student role must not create teacher tasks.\",\"course\":\"Cloud Computing\",\"dueDate\":\"$DUE_DATE\",\"maxScore\":100}")"

echo
echo "7) Teacher JWT to /student returns $TEACHER_TO_STUDENT_ROOT_CODE"
cat "$TMP_DIR/teacher-to-student-root.json"
echo
echo "8) Student JWT to /teacher returns $STUDENT_TO_TEACHER_ROOT_CODE"
cat "$TMP_DIR/student-to-teacher-root.json"
echo
echo "9) Teacher JWT to /student/tasks returns $TEACHER_TO_STUDENT_TASKS_CODE"
cat "$TMP_DIR/teacher-to-student-tasks.json"
echo
echo "10) Teacher JWT to /student/submissions returns $TEACHER_TO_STUDENT_SUBMISSIONS_CODE"
cat "$TMP_DIR/teacher-to-student-submissions.json"
echo
echo "11) Student JWT to GET /teacher/tasks returns $STUDENT_TO_TEACHER_READ_CODE"
cat "$TMP_DIR/student-to-teacher-read.json"
echo
echo "12) Student JWT to POST /teacher/tasks returns $STUDENT_TO_TEACHER_CREATE_CODE"
cat "$TMP_DIR/student-to-teacher-create.json"
echo

if [[ "$TEACHER_TO_STUDENT_ROOT_CODE" != "403" \
  || "$STUDENT_TO_TEACHER_ROOT_CODE" != "403" \
  || "$TEACHER_TO_STUDENT_TASKS_CODE" != "403" \
  || "$TEACHER_TO_STUDENT_SUBMISSIONS_CODE" != "403" \
  || "$STUDENT_TO_TEACHER_READ_CODE" != "403" \
  || "$STUDENT_TO_TEACHER_CREATE_CODE" != "403" ]]; then
  echo "Expected all cross-role checks to return 403" >&2
  exit 1
fi

echo
echo "Postman-equivalent flow passed."
