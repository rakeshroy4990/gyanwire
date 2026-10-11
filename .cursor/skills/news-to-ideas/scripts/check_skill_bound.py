#!/usr/bin/env python3
"""Fail when a news item can skip the news-to-ideas skill.

Checks the project skill and the always-on rule still force the validator.
"""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
SKILL = ROOT / ".cursor" / "skills" / "news-to-ideas" / "SKILL.md"
RULE = ROOT / ".cursor" / "rules" / "news-to-ideas.mdc"
VALIDATOR = Path(__file__).resolve().parent / "validate_flow.py"
SEARCH = Path(__file__).resolve().parent / "github_search.py"

SKILL_BITS = (
    "## Validation gate (never skip)",
    "validate_flow.py",
    "github_search.py",
    "Do not answer on `FAIL`",
    "There is no offline switch.",
    "## Step 1 — Summarise (max 120 words)",
    '## Step 2 — Signal tree (the "noteworthy points")',
    "## Step 3 — Idea candidates → pick ≤ 5",
    "## Step 4 — One subagent per idea (max 5, run in parallel)",
    "## Step 5 — GitHub research (inside each subagent)",
    "## Step 6 — Combine",
    "## Step 7 — Output format (exact)",
)

RULE_BITS = (
    "alwaysApply: true",
    "SKILL.md",
    "validate_flow.py",
    "github_search.py",
    "prints `OK`",
)


def binding_errors() -> list[str]:
    errors: list[str] = []
    if not SKILL.is_file():
        errors.append(f"missing skill: {SKILL}")
    if not RULE.is_file():
        errors.append(f"missing rule: {RULE}")
    if not VALIDATOR.is_file():
        errors.append(f"missing validator: {VALIDATOR}")
    if not SEARCH.is_file():
        errors.append(f"missing GitHub search script: {SEARCH}")
    if errors:
        return errors

    skill = SKILL.read_text(encoding="utf-8")
    rule = RULE.read_text(encoding="utf-8")
    for bit in SKILL_BITS:
        if bit not in skill:
            errors.append(f"skill no longer forces the flow; missing {bit}")
    for bit in RULE_BITS:
        if bit not in rule:
            errors.append(f"news rule no longer forces the flow; missing {bit}")
    if "--offline" in skill or "--offline" in rule:
        errors.append("offline switch would let a news item skip the GitHub check")
    if "alwaysApply: false" in rule:
        errors.append("news rule is not always on, so a news click can skip the skill")
    return errors


def main() -> int:
    errors = binding_errors()
    if errors:
        print("FAIL")
        for item in errors:
            print(f"- {item}")
        return 1
    print("OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
