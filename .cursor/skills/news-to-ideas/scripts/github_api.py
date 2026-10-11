"""GitHub search helpers for the news-to-ideas skill.

The skill allows a repository only when this run's search API returned it.
"""

from __future__ import annotations

import json
import os
import urllib.error
import urllib.parse
import urllib.request

SEARCH_PATH = "/search/repositories"
API_HOST = "api.github.com"
USER_AGENT = "gyanwire-news-to-ideas"


def token_from_env() -> str | None:
    token = os.environ.get("GITHUB_TOKEN", "").strip()
    return token or None


def search_url(terms: str, pushed_after: str | None = None) -> str:
    """Build the skill's repository search URL."""
    query_text = " ".join(terms.split())
    if pushed_after:
        query_text = f"{query_text} pushed:>{pushed_after}"
    query = urllib.parse.urlencode(
        {
            "q": query_text,
            "sort": "stars",
            "order": "desc",
            "per_page": "10",
        }
    )
    return f"https://{API_HOST}{SEARCH_PATH}?{query}"


def explain_search_url(url: str) -> str | None:
    """Return an error when a URL is not the skill's search call."""
    parsed = urllib.parse.urlparse(url)
    if parsed.scheme != "https" or parsed.netloc != API_HOST:
        return "search URL must be https://api.github.com/search/repositories"
    if parsed.path.rstrip("/") != SEARCH_PATH:
        return "search URL must use /search/repositories"
    params = urllib.parse.parse_qs(parsed.query)
    if not params.get("q", [""])[0].strip():
        return "search URL is missing q"
    if params.get("sort", [""])[0] != "stars":
        return "search URL must set sort=stars"
    if params.get("order", [""])[0] != "desc":
        return "search URL must set order=desc"
    if params.get("per_page", [""])[0] != "10":
        return "search URL must set per_page=10"
    return None


def licence_of(item: dict) -> str:
    licence = item.get("license")
    if not isinstance(licence, dict):
        return "unknown"
    spdx = str(licence.get("spdx_id") or "").strip()
    if not spdx:
        return "unknown"
    return spdx


def snapshot_items(payload: dict) -> dict[str, dict]:
    repos: dict[str, dict] = {}
    items = payload.get("items")
    if not isinstance(items, list):
        return repos
    for item in items:
        if not isinstance(item, dict):
            continue
        name = str(item.get("full_name") or "").strip()
        if not name or "/" not in name:
            continue
        pushed = str(item.get("pushed_at") or "")[:10]
        repos[name] = {
            "stars": item.get("stargazers_count"),
            "forks": item.get("forks_count"),
            "licence": licence_of(item),
            "last_push": pushed,
            "archived": bool(item.get("archived")),
            "is_fork": bool(item.get("fork")),
        }
    return repos


def github_fetch(url: str, token: str | None = None) -> dict:
    """Call one search URL. Network and HTTP errors become a status, not a guess."""
    headers = {
        "Accept": "application/vnd.github+json",
        "User-Agent": USER_AGENT,
        "X-GitHub-Api-Version": "2022-11-28",
    }
    if token:
        headers["Authorization"] = f"Bearer {token}"
    request = urllib.request.Request(url, headers=headers)
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            payload = json.loads(response.read().decode("utf-8"))
            status = int(response.status)
    except urllib.error.HTTPError as exc:
        return {"status": int(exc.code), "repos": {}, "error": str(exc.reason)}
    except (urllib.error.URLError, TimeoutError, json.JSONDecodeError, OSError) as exc:
        return {"status": 0, "repos": {}, "error": str(exc)}
    if not isinstance(payload, dict):
        return {"status": status, "repos": {}, "error": "GitHub response was not an object"}
    return {"status": status, "repos": snapshot_items(payload), "error": ""}
