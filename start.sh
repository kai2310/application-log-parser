#!/usr/bin/env bash
set -euo pipefail

# Resolve and switch to the script's own directory so Maven always runs
# against this project's pom.xml even when the script is invoked elsewhere.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${SCRIPT_DIR}"

mvn spring-boot:run
