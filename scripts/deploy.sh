#!/usr/bin/env bash
set -euo pipefail

readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd)"

cd "${PROJECT_ROOT}"

if ! command -v docker >/dev/null 2>&1; then
  echo "Error: Docker is not available. Install or start Docker Desktop first." >&2
  exit 1
fi

if [[ ! -f compose.yaml && ! -f compose.yml && ! -f docker-compose.yaml && ! -f docker-compose.yml ]]; then
  echo "Error: Docker Compose configuration is not available yet; it will be added in step 5." >&2
  exit 1
fi

exec docker compose up --detach --build "$@"
