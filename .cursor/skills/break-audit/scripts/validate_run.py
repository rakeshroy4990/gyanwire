#!/usr/bin/env python3
"""Validate break-audit-run.json. Prints OK or FAIL."""

from __future__ import annotations

import json
import sys
from pathlib import Path

REQUIRED_PHASES = (
    "discover",
    "apiProbe",
    "browserAuth",
    "injection",
    "authz",
    "abuse",
    "functional",
    "ux",
)


def main() -> int:
    if len(sys.argv) != 2:
        print("FAIL usage: validate_run.py break-audit-run.json", file=sys.stderr)
        return 2
    path = Path(sys.argv[1])
    if not path.is_file():
        print(f"FAIL missing file: {path}")
        return 1
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as e:
        print(f"FAIL invalid JSON: {e}")
        return 1

    errors: list[str] = []
    for key in ("site", "api", "email"):
        if not data.get(key):
            errors.append(f"missing {key}")

    if "password" in data:
        errors.append("password must not be stored in the run record")

    phases = data.get("phases")
    if not isinstance(phases, dict):
        errors.append("phases must be an object")
    else:
        for name in REQUIRED_PHASES:
            if phases.get(name) is not True:
                errors.append(f"phase not completed: {name}")

    probe_path = data.get("probeFindingsPath")
    if not probe_path:
        errors.append("missing probeFindingsPath")
    else:
        pp = Path(probe_path)
        if not pp.is_file():
            # also try relative to run file
            alt = path.parent / probe_path
            if not alt.is_file():
                errors.append(f"probe findings missing: {probe_path}")
            else:
                pp = alt
        if pp.is_file():
            try:
                probe = json.loads(pp.read_text(encoding="utf-8"))
            except json.JSONDecodeError:
                errors.append("probe findings not valid JSON")
            else:
                findings = probe.get("findings")
                if not isinstance(findings, list):
                    errors.append("probe findings[] missing")
                else:
                    declared = data.get("findingCount")
                    if declared is not None and int(declared) < len(findings):
                        errors.append(
                            f"findingCount {declared} < probe findings {len(findings)} (do not drop API findings)"
                        )

    if data.get("safeMode") is False and not data.get("destructiveConfirmed"):
        errors.append("safeMode false requires destructiveConfirmed true")

    if errors:
        print("FAIL")
        for e in errors:
            print(f"- {e}")
        return 1

    print("OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
