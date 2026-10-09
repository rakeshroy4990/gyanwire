#!/usr/bin/env bash
# Build and deploy the API to Google Cloud Run via Cloud Build (project: gyanwire).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

GCP_PROJECT="${GCP_PROJECT:-gyanwire}"
GCP_REGION="${GCP_REGION:-asia-south1}"
SERVICE="${SERVICE:-gyanwire}"

if ! command -v gcloud >/dev/null 2>&1; then
  echo "gcloud CLI not found. Install Google Cloud SDK first." >&2
  exit 1
fi

if [[ ! -f cloudbuild.yaml ]]; then
  echo "Missing cloudbuild.yaml in ${ROOT}" >&2
  exit 1
fi

if [[ ! -f package.json || ! -f package-lock.json ]]; then
  echo "Missing package.json or package-lock.json in ${ROOT}" >&2
  exit 1
fi

if ! command -v npm >/dev/null 2>&1; then
  echo "npm not found. Install Node.js/npm so the lockfile can be verified before deploy." >&2
  exit 1
fi

# Docker ui-build runs `npm ci`, which requires package.json and package-lock.json in sync.
echo "Checking package-lock.json is in sync with package.json..."
if ! npm ci --dry-run --ignore-scripts >/dev/null 2>&1; then
  echo "Lockfile out of sync; updating package-lock.json..."
  npm install --package-lock-only
  if ! npm ci --dry-run --ignore-scripts >/dev/null 2>&1; then
    echo "package-lock.json still out of sync after update. Fix locally and retry." >&2
    exit 1
  fi
  echo "Updated package-lock.json (commit it so future deploys stay green)."
fi

echo "Submitting Cloud Build → project ${GCP_PROJECT}..."
gcloud builds submit \
  --project="${GCP_PROJECT}" \
  --config=cloudbuild.yaml \
  .

echo ""
echo "Fetching Cloud Run URL..."
URL="$(gcloud run services describe "${SERVICE}" \
  --project="${GCP_PROJECT}" \
  --region="${GCP_REGION}" \
  --format='value(status.url)' 2>/dev/null || true)"

if [[ -n "${URL}" ]]; then
  echo "API deployed: ${URL}"
  echo "Health:       ${URL}/api/health"
else
  echo "Deploy finished, but could not resolve service URL for ${SERVICE}."
fi
