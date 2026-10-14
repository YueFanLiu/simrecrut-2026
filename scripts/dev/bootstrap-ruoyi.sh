#!/usr/bin/env bash
# Delegate CI bootstrap to the backend's Docker-compatible dependency installer.
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
bash "$root/backend/java/.mvn/bootstrap-ruoyi.sh"
