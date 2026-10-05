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
