#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [[ -f .env ]]; then
  # Load PORT / UI_PORT from .env without exporting unrelated values blindly.
  while IFS= read -r line || [[ -n "$line" ]]; do
    case "$line" in
      ''|\#*) continue ;;
    esac
    key="${line%%=*}"
    value="${line#*=}"
    case "$key" in
      UI_PORT|PORT|HOST)
        # shellcheck disable=SC2163
        export "$key=$value"
        ;;
    esac
  done < .env
fi

UI_PORT="${UI_PORT:-5180}"
API_PORT="${PORT:-3010}"

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

export PORT="$API_PORT"
export HOST="${HOST:-0.0.0.0}"

if [[ ! -d node_modules ]]; then
  echo "Installing dependencies..."
  npm install
fi

echo "Starting Gyanwire..."
echo "  UI  http://localhost:$UI_PORT"
echo "  API http://localhost:$API_PORT"
exec npm run dev
