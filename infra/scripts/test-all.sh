#!/usr/bin/env bash
# Run mvn clean verify on every Spring Boot service. Fails fast if any
# service's tests or coverage gate (≥80% line) fail.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

for svc in Registration_Microservice Authentication_Microservice Student_Microservice Teacher_Microservice APIGateway_Microservice; do
  echo
  echo "================ $svc ================"
  ( cd "$ROOT/$svc" && mvn -B clean verify )
done

echo
echo "All services passed."
