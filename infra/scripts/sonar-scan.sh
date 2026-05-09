#!/usr/bin/env bash
# Run SonarQube analysis for all five microservices. Each service is a
# separate Sonar project, mirroring the Maven independence.
#
# Prereqs:
#   1. SonarQube up:  docker-compose -f docker-compose.sonar.yml up -d
#   2. SONAR_TOKEN exported (User Token from http://localhost:9000)
#   3. Tests already green:  ./infra/scripts/test-all.sh   (or per-service mvn verify)

set -euo pipefail

: "${SONAR_TOKEN:?SONAR_TOKEN must be set — generate one at http://localhost:9000/account/security}"
SONAR_HOST="${SONAR_HOST:-http://localhost:9000}"

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

scan() {
  local svc="$1"
  echo
  echo "==> sonar:sonar  $svc"
  ( cd "$ROOT/$svc" && \
    mvn -B -DskipTests verify sonar:sonar \
      -Dsonar.host.url="$SONAR_HOST" \
      -Dsonar.token="$SONAR_TOKEN" )
}

# verify (with tests + jacoco) was assumed run already — if not, do it now:
if [ "${RUN_TESTS_FIRST:-true}" = "true" ]; then
  for svc in Registration_Microservice Authentication_Microservice Student_Microservice Teacher_Microservice APIGateway_Microservice; do
    echo "==> mvn verify  $svc"
    ( cd "$ROOT/$svc" && mvn -B clean verify )
  done
fi

scan Registration_Microservice
scan Authentication_Microservice
scan Student_Microservice
scan Teacher_Microservice
scan APIGateway_Microservice

echo
echo "Done. Open $SONAR_HOST/projects to see the five Sonar projects."
