#!/usr/bin/env bash
# Seed one STUDENT and one TEACHER account so you can demo the JWT flow.
set -euo pipefail

GATEWAY="${GATEWAY:-http://localhost:8080}"

curl -fsS -X POST "$GATEWAY/auth/register" \
  -H 'Content-Type: application/json' \
  -d '{"email":"student1@aupp.edu","password":"Student#123","role":"STUDENT","fullName":"Sothea Student"}' || true
echo

curl -fsS -X POST "$GATEWAY/auth/register" \
  -H 'Content-Type: application/json' \
  -d '{"email":"teacher1@aupp.edu","password":"Teacher#123","role":"TEACHER","fullName":"Dara Teacher"}' || true
echo

echo "==> Seed complete. Login:"
echo "   POST $GATEWAY/auth/login { \"email\":\"student1@aupp.edu\", \"password\":\"Student#123\" }"
