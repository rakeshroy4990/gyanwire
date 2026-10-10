#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [[ -f .env ]]; then
  while IFS= read -r line || [[ -n "$line" ]]; do
    case "$line" in
      ''|\#*) continue ;;
    esac
    key="${line%%=*}"
    value="${line#*=}"
    case "$key" in
      GYANWIRE_SERVER_PORT|SPRING_DATASOURCE_URL|SPRING_DATASOURCE_USERNAME|SPRING_DATASOURCE_PASSWORD|APP_CORS_ALLOWED_ORIGIN_PATTERNS|APP_PERSISTENCE_PROVIDER|JWT_SECRET|APP_AUTH_JWT_SECRET|JWT_ACCESS_TTL_SECONDS|JWT_REFRESH_TTL_SECONDS|AUTH_COOKIE_SECURE|AUTH_COOKIE_SAME_SITE|AUTH_COOKIE_DOMAIN|UI_ORIGIN|LLM_API_KEY|LLM_BASE_URL|LLM_MODEL|SEARXNG_URL|RAZORPAY_KEY_ID|RAZORPAY_KEY_SECRET|RAZORPAY_WEBHOOK_SECRET|RAZORPAY_PLAN_PRO_MONTHLY|RAZORPAY_PLAN_PRO_ANNUAL|RAZORPAY_PLAN_TEAM_MONTHLY|RAZORPAY_PLAN_TEAM_ANNUAL)
        # shellcheck disable=SC2163
        export "$key=$value"
        ;;
    esac
  done < .env
fi

PORT="${GYANWIRE_SERVER_PORT:-8080}"
export GYANWIRE_SERVER_PORT="$PORT"
export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-dev}"
if [[ -n "${JWT_SECRET:-}" && -z "${APP_AUTH_JWT_SECRET:-}" ]]; then
  export APP_AUTH_JWT_SECRET="$JWT_SECRET"
fi

kill_port() {
  local port="$1"
  local attempt
  local pids

  for attempt in 1 2 3; do
    pids="$(lsof -nP -tiTCP:"$port" -sTCP:LISTEN 2>/dev/null || true)"
    if [[ -z "$pids" ]]; then
      echo "Port $port is free."
      return 0
    fi

    echo "Port $port is occupied by: $pids"
    if [[ "$attempt" -eq 1 ]]; then
      echo "Killing process(es) on port $port..."
      # shellcheck disable=SC2086
      kill $pids 2>/dev/null || true
    else
      echo "Force killing process(es) on port $port..."
      # shellcheck disable=SC2086
      kill -9 $pids 2>/dev/null || true
    fi
    sleep 0.5
  done

  if lsof -nP -tiTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then
    echo "Could not free port $port." >&2
    exit 1
  fi

  echo "Port $port is free."
}

echo "Gyanwire server → :$PORT"
kill_port "$PORT"

if [[ ! -x "$ROOT/gyanwire-server/gradlew" ]]; then
  echo "Missing gyanwire-server/gradlew" >&2
  exit 1
fi

echo "Starting gyanwire-server on http://localhost:$PORT"
cd "$ROOT/gyanwire-server"
exec ./gradlew bootRun
