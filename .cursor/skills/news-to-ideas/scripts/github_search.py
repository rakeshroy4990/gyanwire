#!/usr/bin/env python3
"""Run the skill's GitHub repository searches.

Step 5 must use this script. Copy repositories only from the JSON it prints.

Usage:
  python3 github_search.py "community health toolkit"
  python3 github_search.py --pushed 2024-10-11 "volunteer case management" "cht core"
"""

from __future__ import annotations

import json
import re
import sys

from github_api import github_fetch, search_url, token_from_env

PUSHED_RE = re.compile(r"^\d{4}-\d{2}-\d{2}$")


def main(argv: list[str] | None = None) -> int:
    args = list(sys.argv[1:] if argv is None else argv)
    pushed_after = None
    if args and args[0].startswith("--pushed"):
        raw = args.pop(0)
        pushed_after = raw.split("=", 1)[1] if "=" in raw else (args.pop(0) if args else "")
        if not PUSHED_RE.match(pushed_after or ""):
            print("FAIL")
            print("- --pushed needs YYYY-MM-DD")
            return 1
    terms = [term.strip() for term in args if term.strip()]
    if not terms:
        print("FAIL")
        print('- usage: python3 github_search.py [--pushed YYYY-MM-DD] "<terms>" ["<more terms>"]')
        return 1

    token = token_from_env()
    queries = []
    for term in terms:
        url = search_url(term, pushed_after)
        result = github_fetch(url, token)
        repos = []
        for name, snap in result["repos"].items():
            repos.append({"repo": name, "url": f"https://github.com/{name}", **snap})
        queries.append(
            {
                "url": url,
                "status": result["status"],
                "error": result["error"],
                "repos": repos,
            }
        )
    json.dump({"queries": queries}, sys.stdout, indent=2)
    sys.stdout.write("\n")
    if any(query["status"] != 200 for query in queries):
        print("FAIL", file=sys.stderr)
        print("- GitHub search did not return 200. Mark that idea GitHub unverified and name no repo.", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
