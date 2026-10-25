#!/bin/sh
set -eu

url=${1:?Usage: smoke-test.sh BASE_URL ENVIRONMENT COMMIT}
environment=${2:?Expected environment is required}
commit=${3:?Expected commit is required}
url=${url%/}
command -v python3 >/dev/null 2>&1 || command -v python >/dev/null 2>&1 || { echo 'Smoke test requires python3 to validate JSON.' >&2; exit 1; }
PY_CMD=$(command -v python3 2>/dev/null || command -v python)

fetch() {
    response=$(curl --fail --silent --show-error --connect-timeout 2 --max-time 3 \
        --write-out '\n%{http_code}' "$1") || return 1
    status=$(printf '%s' "$response" | tail -n 1)
    [ "$status" = 200 ] || return 1
    printf '%s\n' "$response" | sed '$d'
}

attempt=1
while [ "$attempt" -le 30 ]; do
    if info=$(fetch "$url/info") && \
       printf '%s' "$info" | "$PY_CMD" -c '
import json, sys
try:
    body = json.load(sys.stdin)
    valid = isinstance(body, dict) and all(body.get(k) == v for k, v in {
        "application": "psoft-g1", "environment": sys.argv[1], "commit": sys.argv[2]
    }.items())
except (ValueError, TypeError):
    valid = False
sys.exit(0 if valid else 1)
' "$environment" "$commit" && \
       (health=$(fetch "$url/actuator/health/readiness") || health=$(fetch "$url/actuator/health")) && \
       printf '%s' "$health" | "$PY_CMD" -c '
import json, sys
try:
    body = json.load(sys.stdin)
    valid = isinstance(body, dict) and body.get("status") == "UP"
except (ValueError, TypeError):
    valid = False
sys.exit(0 if valid else 1)
'; then
        printf 'Smoke test passed: %s (psoft-g1, %s, %s)\n' "$url" "$environment" "$commit"
        exit 0
    fi
    printf 'Waiting for expected application and readiness (%s/30): %s\n' "$attempt" "$url"
    sleep 2
    attempt=$((attempt + 1))
done
printf 'Smoke test failed: %s (expected psoft-g1, %s, %s)\n' "$url" "$environment" "$commit" >&2
exit 1

