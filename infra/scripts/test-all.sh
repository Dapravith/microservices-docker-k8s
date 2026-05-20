#!/usr/bin/env bash
# Smoke test the full flow. Use after seed-users.sh.
set -euo pipefail

GATEWAY="${GATEWAY:-http://localhost:8080}"

login() {
  local email="$1" pwd="$2"
  curl -fsS -X POST "$GATEWAY/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"$email\",\"password\":\"$pwd\"}" \
    | python3 -c 'import json,sys; print(json.load(sys.stdin)["token"])'
}

echo "==> Logging in as student1"
STUDENT_JWT=$(login student1@aupp.edu Student#123)
echo "==> Logging in as teacher1"
TEACHER_JWT=$(login teacher1@aupp.edu Teacher#123)

echo
echo "==> 1) /student/me with STUDENT JWT (expect 200)"
curl -i -H "Authorization: Bearer $STUDENT_JWT" "$GATEWAY/student/me"
echo
echo "==> 2) /teacher/me with TEACHER JWT (expect 200)"
curl -i -H "Authorization: Bearer $TEACHER_JWT" "$GATEWAY/teacher/me"
echo
echo "==> 3) /student/me with TEACHER JWT (expect 403)"
curl -i -H "Authorization: Bearer $TEACHER_JWT" "$GATEWAY/student/me"
echo
echo "==> 4) /teacher/me with STUDENT JWT (expect 403)"
curl -i -H "Authorization: Bearer $STUDENT_JWT" "$GATEWAY/teacher/me"
echo
echo "==> 5) /student/me with no JWT (expect 401)"
curl -i "$GATEWAY/student/me"
