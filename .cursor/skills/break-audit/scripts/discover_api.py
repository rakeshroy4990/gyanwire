#!/usr/bin/env python3
"""Guess API base URL for a site. Prints JSON to stdout."""

from __future__ import annotations

import argparse
import json
import urllib.error
import urllib.request
from urllib.parse import urlparse


CANDIDATES = (
    "http://localhost:8080",
    "http://127.0.0.1:8080",
    "http://localhost:8081",
)


def probe(base: str, timeout: float = 3.0) -> dict:
    url = base.rstrip("/") + "/api/health"
    try:
        req = urllib.request.Request(url, method="GET")
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            body = resp.read()[:500].decode("utf-8", errors="replace")
            return {"ok": True, "status": resp.status, "bodyPreview": body}
    except urllib.error.HTTPError as e:
        body = e.read()[:300].decode("utf-8", errors="replace")
        return {"ok": False, "status": e.code, "bodyPreview": body}
    except Exception as e:  # noqa: BLE001
        return {"ok": False, "status": None, "error": str(e)}


def same_origin_api(site: str) -> str:
    p = urlparse(site)
    if not p.scheme or not p.netloc:
        return ""
    return f"{p.scheme}://{p.netloc}"


def main() -> int:
    parser = argparse.ArgumentParser(description="Discover Gyanwire-style API base")
    parser.add_argument("--site", required=True, help="UI origin, e.g. http://localhost:5180/")
    parser.add_argument("--api", default="", help="Force API base if known")
    args = parser.parse_args()

    tried: list[dict] = []
    chosen = None

    ordered: list[str] = []
    if args.api:
        ordered.append(args.api.rstrip("/"))
    # Prefer dedicated API ports before same-origin UI proxies (Vite often proxies /api).
    for c in CANDIDATES:
        if c not in ordered:
            ordered.append(c)
    same = same_origin_api(args.site)
    if same and same not in ordered:
        ordered.append(same)

    for base in ordered:
        result = probe(base)
        entry = {"api": base, **result}
        tried.append(entry)
        if result.get("ok") or result.get("status") == 200:
            chosen = base
            break
        # Some health endpoints return JSON 200 only; also accept any HTTP response from server
        if result.get("status") is not None and result["status"] < 500:
            chosen = base
            break

    out = {
        "site": args.site.rstrip("/") + "/",
        "api": chosen,
        "tried": tried,
    }
    print(json.dumps(out, indent=2))
    return 0 if chosen else 1


if __name__ == "__main__":
    raise SystemExit(main())
