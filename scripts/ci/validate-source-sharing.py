#!/usr/bin/env python3
"""Reject exact Java copies that should be owned by a shared source root."""

from __future__ import annotations

import hashlib
from pathlib import Path
import sys


ROOT = Path(__file__).resolve().parents[2]
PLATFORM = ROOT / "platform"


def java_sources() -> list[Path]:
    result: list[Path] = []
    for source in PLATFORM.rglob("*.java"):
        relative = source.relative_to(PLATFORM)
        parts = relative.parts
        if "src" not in parts or "java" not in parts:
            continue
        result.append(source)
    return sorted(result)


def main() -> int:
    by_digest: dict[str, list[Path]] = {}
    for source in java_sources():
        digest = hashlib.sha256(source.read_bytes()).hexdigest()
        by_digest.setdefault(digest, []).append(source)

    duplicate_groups = [paths for paths in by_digest.values() if len(paths) > 1]
    if duplicate_groups:
        print("Exact Java copies must move to a shared or API-era source root:", file=sys.stderr)
        for paths in duplicate_groups:
            for source in paths:
                print(f"  {source.relative_to(ROOT)}", file=sys.stderr)
            print(file=sys.stderr)
        return 1

    stale_donors: list[Path] = []
    marker = "platform/neoforge-1.21.1/src/"
    for build in sorted(PLATFORM.glob("neoforge-*/build.gradle")):
        if marker in build.read_text(encoding="utf-8"):
            stale_donors.append(build)
    if stale_donors:
        print("NeoForge targets must consume neoforge-common, not a version module:", file=sys.stderr)
        for build in stale_donors:
            print(f"  {build.relative_to(ROOT)}", file=sys.stderr)
        return 1

    print(f"Verified shared-source ownership across {len(java_sources())} Java source files.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
