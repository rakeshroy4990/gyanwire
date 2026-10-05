#!/usr/bin/env bash
# Build the Vue UI and deploy to Firebase Hosting (project: gyanwire-a4293).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

FIREBASE_PROJECT="${FIREBASE_PROJECT:-gyanwire-a4293}"
UI_URL="${UI_URL:-https://${FIREBASE_PROJECT}.web.app}"

if ! command -v firebase >/dev/null 2>&1; then
  echo "firebase CLI not found. Install with: npm install -g firebase-tools" >&2
  exit 1
fi

if [[ ! -d node_modules ]]; then
  echo "Installing dependencies..."
  npm install
fi

if [[ ! -f .env.production ]]; then
  echo "Missing .env.production (needs VITE_BACKEND_URL + VITE_GOOGLE_OAUTH_CLIENT_ID)." >&2
  exit 1
fi

if ! grep -Eq '^VITE_BACKEND_URL=https?://' .env.production; then
  echo "Missing VITE_BACKEND_URL in .env.production (Cloud Run origin)." >&2
  exit 1
fi

echo "Building UI (Vite production)..."
# Vite loads .env.production automatically for production builds.
npm run build

echo "Deploying hosting → Firebase project ${FIREBASE_PROJECT}..."
firebase deploy --only hosting --project "${FIREBASE_PROJECT}"

echo ""
echo "UI deployed: ${UI_URL}"
echo "API target:  $(grep -E '^VITE_BACKEND_URL=' .env.production | cut -d= -f2- || true)"
