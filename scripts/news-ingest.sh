#!/usr/bin/env bash
set -euo pipefail

industry=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --industry)
      industry="${2:-}"
      shift 2
      ;;
    *)
      shift
      ;;
  esac
done

port="${GYANWIRE_SERVER_PORT:-8080}"
url="http://localhost:${port}/api/admin/news/ingest"
if [[ -n "$industry" ]]; then
  url="${url}?industry=${industry}"
fi

curl -sS -X POST "$url"
echo
