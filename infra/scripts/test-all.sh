#!/usr/bin/env bash
# Run mvn clean verify on every Spring Boot service. Fails fast if any
# service's tests or coverage gate (≥80% line) fail.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

for svc in registration-service login-service student-service teacher-service api-gateway; do
  echo
  echo "================ $svc ================"
  ( cd "$ROOT/$svc" && mvn -B clean verify )
done

echo
echo "All services passed."
