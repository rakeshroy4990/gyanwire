#!/usr/bin/env bash
# Free UI + API ports if busy, then start gyanwire-server and Vite UI together.
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
      UI_PORT|GYANWIRE_SERVER_PORT|PORT|SPRING_DATASOURCE_URL|SPRING_DATASOURCE_USERNAME|SPRING_DATASOURCE_PASSWORD|APP_CORS_ALLOWED_ORIGIN_PATTERNS|APP_PERSISTENCE_PROVIDER|JWT_SECRET|APP_AUTH_JWT_SECRET|JWT_ACCESS_TTL_SECONDS|JWT_REFRESH_TTL_SECONDS|AUTH_COOKIE_SECURE|AUTH_COOKIE_SAME_SITE|AUTH_COOKIE_DOMAIN|UI_ORIGIN|VITE_BACKEND_URL|VITE_GOOGLE_OAUTH_CLIENT_ID|LLM_API_KEY|LLM_BASE_URL|LLM_MODEL|SEARXNG_URL|RAZORPAY_KEY_ID|RAZORPAY_KEY_SECRET|RAZORPAY_WEBHOOK_SECRET|RAZORPAY_PLAN_PRO_MONTHLY|RAZORPAY_PLAN_PRO_ANNUAL|RAZORPAY_PLAN_TEAM_MONTHLY|RAZORPAY_PLAN_TEAM_ANNUAL)
        # shellcheck disable=SC2163
        export "$key=$value"
        ;;
    esac
  done < .env
fi

UI_PORT="${UI_PORT:-5180}"
API_PORT="${GYANWIRE_SERVER_PORT:-${PORT:-8080}}"
export GYANWIRE_SERVER_PORT="$API_PORT"
export UI_PORT

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

echo "Gyanwire → UI :$UI_PORT  API :$API_PORT"
kill_port "$UI_PORT"
kill_port "$API_PORT"

if [[ ! -d node_modules ]]; then
  echo "Installing dependencies..."
  npm install
fi

if [[ ! -x "$ROOT/gyanwire-server/gradlew" ]]; then
  echo "Missing gyanwire-server/gradlew" >&2
  exit 1
fi

echo "Starting gyanwire-server + UI..."
echo "  UI  http://localhost:$UI_PORT"
echo "  API http://localhost:$API_PORT"

# Avoid nested kill/restart scripts; ports are already free.
exec npx concurrently -k \
  -n api,ui \
  -c blue,green \
  "cd gyanwire-server && ./gradlew bootRun" \
  "npm run dev:client"
