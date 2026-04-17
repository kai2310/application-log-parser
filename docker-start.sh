#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${SCRIPT_DIR}"

IMAGE_NAME="${IMAGE_NAME:-application-log-parser:latest}"
HOST_PORT="${HOST_PORT:-8080}"
CONTAINER_PORT="${CONTAINER_PORT:-8080}"
LOGS_DIR="${LOGS_DIR:-}"
REPORTS_DIR="${REPORTS_DIR:-}"

DOCKER_RUN_ARGS=(--rm -p "${HOST_PORT}:${CONTAINER_PORT}")

if [[ -n "${LOGS_DIR}" ]]; then
  DOCKER_RUN_ARGS+=(-v "${LOGS_DIR}:/logs:ro")
fi

if [[ -n "${REPORTS_DIR}" ]]; then
  DOCKER_RUN_ARGS+=(-v "${REPORTS_DIR}:/app/reports")
fi

docker build -t "${IMAGE_NAME}" .
docker run "${DOCKER_RUN_ARGS[@]}" "${IMAGE_NAME}"
