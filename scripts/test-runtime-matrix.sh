#!/usr/bin/env bash
# Stable command-line entrypoint for the event-driven runtime acceptance runner.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
exec python3 "$root/scripts/test-runtime-matrix.py" "$@"
