#!/usr/bin/env python3
"""Reject a news-to-ideas run that skipped a skill step.

Usage:
  python3 validate_flow.py run.json

The command calls GitHub for every recorded search URL. Exit 0 prints OK.
Anything else prints FAIL and the news item is not done.

Run record (JSON object):
  headline, sources, publish_date, region, industry, india_first
  elsewhere_reason (required when india_first is false)
  summary, facts, opinions, conflicts
  signals: [{id, text, type, evidence, inferred}]
  scores: [{idea_id, title, one_liner, target_user, problem, signals,
            urgency, market_reach, timing, buildability, defensibility,
            fresher_friendly}]
  subagents: [{idea_id, title, one_liner, signals, target_user, keywords}]
  github: {idea_id: {unverified, calls: [{url, status, repos}],
           projects: [{repo, url, stars, forks, downloads, licence, category,
                       last_push, archived, stale, is_fork, fit, why_fit}],
           paid_alternatives: [{name, note}], gaps}}
  ideas: [{idea_id, title, one_liner, who_problem_why_now, signals, derisk,
           confidence}]
  empty_reason (required when ideas is empty)
  coverage_notes, output_markdown

Scores are integers 1–5. Weights: urgency ×2, market reach ×2, timing ×1.5,
buildability ×1, defensibility ×1, fresher-friendly ×0.5. Maximum is 40.
"""

from __future__ import annotations

import json
import re
import sys
from collections import Counter
from datetime import date, datetime, timedelta
from pathlib import Path

from github_api import explain_search_url, github_fetch, token_from_env

INDUSTRIES = {
    "Share Market",
    "IT",
    "Medical",
    "Space",
    "Social Media",
    "Gaming",
    "Astrology",
    "other",
}
SIGNAL_TYPES = {
    "policy",
    "funding/budget",
    "scale/number",
    "new-org-or-structure",
    "tech/digital-tool",
    "shortage/gap",
    "behaviour/stigma",
    "regulation/compliance",
    "partnership",
    "timeline/deadline",
    "risk/failure",
}
DERISK = {"regulated", "needs-govt-data", "needs-field-ops", "pure-software"}
CONFIDENCE = {"high", "medium", "low"}
CATEGORIES = {
    "open-source",
    "open-source-copyleft",
    "source-available",
    "paid",
    "unknown",
}
DOWNLOAD_SOURCES = {"npm", "pypi", "docker", "release", "none"}
HEADINGS = (
    "## News in brief",
    "## Noteworthy points",
    "## Idea shortlist (score table)",
    "## Ideas",
    "## Coverage notes",
)
DIMENSIONS = (
    "Urgency",
    "Market reach",
    "Timing",
    "Buildability",
    "Defensibility",
    "Fresher",
)
REQUIRED = (
    "headline",
    "sources",
    "publish_date",
    "region",
    "industry",
    "india_first",
    "summary",
    "facts",
    "opinions",
    "conflicts",
    "signals",
    "scores",
    "subagents",
    "github",
    "ideas",
    "coverage_notes",
    "output_markdown",
)
PLAN_MARKERS = (
    "roadmap",
    "go-to-market",
    "go to market",
    "gtm plan",
    "hiring plan",
    "hire a",
    "tech stack",
    "tech-stack",
    "week 1",
    "week 2",
    "90-day plan",
    "budget of",
    "pricing model",
    "pricing plan",
)
UNSAFE = (
    "diagnos",
    "prescrib",
    "you should invest",
    "buy this stock",
    "guaranteed return",
    "how to scam",
    "spy on",
    "covert surveillance",
)
INDIA_MARKERS = ("india", "₹", "inr", "rupee", "tier-2", "tier 2")
REPO_RE = re.compile(
    r"\b[A-Za-z0-9_.-]*[A-Za-z][A-Za-z0-9_.-]*/[A-Za-z0-9_.-]*[A-Za-z][A-Za-z0-9_.-]*\b"
)
IDEA_HEAD_RE = re.compile(
    r"^###\s+(I[1-5])\s+—\s+.*\(\s*score\s+([0-9]+(?:\.[0-9]+)?)\s*/\s*([0-9]+(?:\.[0-9]+)?)\s*\)\s*$"
)
SIGNAL_ID_RE = re.compile(r"^S\d+(?:\.\d+)*$")
SCORE_FIELDS = (
    "urgency",
    "market_reach",
    "timing",
    "buildability",
    "defensibility",
    "fresher_friendly",
)


def words(text: str) -> list[str]:
    return text.split()


def score_value(row: dict) -> float:
    return (
        row["urgency"] * 2
        + row["market_reach"] * 2
        + row["timing"] * 1.5
        + row["buildability"]
        + row["defensibility"]
        + row["fresher_friendly"] * 0.5
    )


def score_labels(value: float) -> set[str]:
    text = f"{value:.1f}"
    if text.endswith(".0"):
        return {text, text[:-2]}
    return {text}


def is_score(value: object) -> bool:
    return isinstance(value, int) and not isinstance(value, bool) and 1 <= value <= 5


def expected_category(licence: str) -> str | None:
    key = licence.strip().lower().replace("-only", "").replace("-or-later", "")
    if key in {"mit", "apache-2.0", "bsd-2-clause", "bsd-3-clause", "isc", "unlicense", "0bsd", "zlib"}:
        return "open-source"
    if key.startswith("apache-") or key.startswith("bsd"):
        return "open-source"
    if "gpl" in key or key.startswith("mpl"):
        return "open-source-copyleft"
    if key in {"bsl-1.0", "sspl-1.0", "elastic-2.0", "noassertion", "none"}:
        return "source-available"
    if key == "unknown":
        return "unknown"
    return None


def string_list(value: object, label: str, errors: list[str], allow_empty: bool) -> list[str] | None:
    if not isinstance(value, list) or (not value and not allow_empty):
        errors.append(f"{label} must be a non-empty list" if not allow_empty else f"{label} must be a list")
        return None
    if not all(isinstance(item, str) and item.strip() for item in value):
        errors.append(f"{label} must be non-empty strings")
        return None
    return value


def has_token(text: str, token: str) -> bool:
    return re.search(rf"(?<![\w.]){re.escape(token)}(?![\w.])", text) is not None


def brief_body(section: str) -> str:
    text = section.strip()
    for marker in ("· Sources:", "Sources:"):
        index = text.find(marker)
        if index != -1:
            text = text[:index]
    return text.strip()


def sections(markdown: str) -> tuple[dict[str, str], list[str]]:
    errors: list[str] = []
    lines = markdown.splitlines()
    found: list[tuple[int, str]] = []
    for index, line in enumerate(lines):
        if line.startswith("## "):
            found.append((index, line.strip()))
    titles = [title for _, title in found]
    if titles != list(HEADINGS):
        errors.append(
            "Step 7 skipped: output headings must be exactly "
            + " | ".join(HEADINGS)
        )
        return {}, errors
    chunks: dict[str, str] = {}
    for position, (index, title) in enumerate(found):
        end = found[position + 1][0] if position + 1 < len(found) else len(lines)
        chunks[title] = "\n".join(lines[index + 1 : end]).strip()
    return chunks, errors


def bullet(block: str, label: str) -> str | None:
    prefix = f"- **{label}:**"
    for line in block.splitlines():
        if line.startswith(prefix):
            return line[len(prefix) :].strip()
    return None


def check_input(record: dict, errors: list[str]) -> None:
    headline = record.get("headline")
    if not isinstance(headline, str) or not headline.strip():
        errors.append("Input skipped: headline is empty")
    sources = string_list(record.get("sources"), "Input skipped: sources", errors, False)
    if sources is not None and len(sources) != len({item.strip() for item in sources}):
        errors.append("Input skipped: duplicate sources")
    published = record.get("publish_date")
    if not isinstance(published, str) or not published.strip():
        errors.append("Input skipped: publish_date is empty")
    region = record.get("region")
    if not isinstance(region, str) or not region.strip():
        errors.append("Input skipped: region is empty")
    if record.get("industry") not in INDUSTRIES:
        errors.append("Input skipped: industry is not a Gyanwire industry or other")
    india_first = record.get("india_first")
    if not isinstance(india_first, bool):
        errors.append("Input skipped: india_first must be true or false")
    elif not india_first:
        reason = record.get("elsewhere_reason")
        if not isinstance(reason, str) or len(reason.strip()) < 8:
            errors.append("Input skipped: news outside India needs elsewhere_reason")


def check_summary(record: dict, errors: list[str]) -> None:
    summary = record.get("summary")
    if not isinstance(summary, str) or not summary.strip():
        errors.append("Step 1 skipped: summary is empty")
        return
    count = len(words(summary))
    if count > 120:
        errors.append(f"Step 1 skipped: summary is {count} words; maximum is 120")
    facts = string_list(record.get("facts"), "Step 1 skipped: facts", errors, False)
    opinions = string_list(record.get("opinions"), "Step 1 skipped: opinions", errors, True)
    conflicts = string_list(record.get("conflicts"), "Step 1 skipped: conflicts", errors, True)
    if facts:
        for fact in facts:
            if fact not in summary:
                errors.append("Step 1 skipped: a fact is missing from the summary")
                break
    if opinions:
        for opinion in opinions:
            if opinion not in summary:
                errors.append("Step 1 skipped: an opinion is missing from the summary")
                break
    if conflicts:
        for conflict in conflicts:
            if conflict not in summary:
                errors.append("Step 1 skipped: a conflict is missing from the summary")
                break
        if not re.search(r"\bconflict\b", summary, re.I):
            errors.append("Step 1 skipped: conflicting numbers must be flagged conflict")


def check_signals(record: dict, errors: list[str]) -> set[str]:
    signals = record.get("signals")
    if not isinstance(signals, list) or not signals:
        errors.append("Step 2 skipped: no signals")
        return set()
    ids: list[str] = []
    for index, signal in enumerate(signals, start=1):
        if not isinstance(signal, dict):
            errors.append(f"Step 2 skipped: signal {index} is not an object")
            continue
        sid = signal.get("id")
        if not isinstance(sid, str) or not SIGNAL_ID_RE.match(sid):
            errors.append(f"Step 2 skipped: signal {index} needs an id like S1 or S1.1")
            sid = ""
        else:
            ids.append(sid)
        text = signal.get("text")
        evidence = signal.get("evidence")
        if not isinstance(text, str) or not text.strip() or "\n" in text:
            errors.append(f"Step 2 skipped: {sid or index} needs one fact")
        if not isinstance(evidence, str) or not evidence.strip():
            errors.append(f"Step 2 skipped: {sid or index} needs evidence")
        if signal.get("type") not in SIGNAL_TYPES:
            errors.append(f"Step 2 skipped: {sid or index} has a signal type outside the skill")
        if not isinstance(signal.get("inferred"), bool):
            errors.append(f"Step 2 skipped: {sid or index} must set inferred true or false")
    if len(ids) != len(set(ids)):
        errors.append("Step 2 skipped: duplicate signal ids")
    id_set = set(ids)
    for sid in ids:
        if "." in sid and sid.rsplit(".", 1)[0] not in id_set:
            errors.append(f"Step 2 skipped: {sid} has no parent signal")
    return id_set


def check_scores(scores: list, signal_ids: set[str], errors: list[str]) -> dict[str, dict]:
    rows: dict[str, dict] = {}
    if not isinstance(scores, list):
        errors.append("Step 3 skipped: scores must be a list")
        return rows
    users: list[tuple[str, str]] = []
    for index, row in enumerate(scores, start=1):
        if not isinstance(row, dict):
            errors.append(f"Step 3 skipped: score {index} is not an object")
            continue
        idea_id = row.get("idea_id")
        if not isinstance(idea_id, str) or idea_id in rows:
            errors.append(f"Step 3 skipped: score {index} needs a unique idea id")
            continue
        for field in ("title", "one_liner", "target_user", "problem"):
            value = row.get(field)
            if not isinstance(value, str) or not value.strip():
                errors.append(f"Step 3 skipped: {idea_id} is missing {field}")
        cited = string_list(row.get("signals"), f"Step 3 skipped: {idea_id} signals", errors, False)
        if cited:
            unknown = [item for item in cited if item not in signal_ids]
            if unknown:
                errors.append(f"Step 3 skipped: {idea_id} cites {', '.join(unknown)} which is not in the signal tree")
        for field in SCORE_FIELDS:
            if not is_score(row.get(field)):
                errors.append(f"Step 3 skipped: {idea_id} {field} must be an integer 1–5")
        if all(is_score(row.get(field)) for field in SCORE_FIELDS):
            rows[idea_id] = row
        user = str(row.get("target_user") or "").strip().lower()
        problem = str(row.get("problem") or "").strip().lower()
        if user and problem:
            users.append((user, problem))
    counts = Counter(user for user, _problem in users)
    for user, count in counts.items():
        if count > 2:
            errors.append(f"Step 3 skipped: more than 2 ideas for user group {user}")
    if len(users) != len(set(users)):
        errors.append("Step 3 skipped: duplicate user and problem")
    return rows


def check_subagents(subagents: object, idea_ids: list[str], errors: list[str]) -> None:
    if not isinstance(subagents, list):
        errors.append("Step 4 skipped: subagents must be a list")
        return
    seen: list[str] = []
    for index, agent in enumerate(subagents, start=1):
        if not isinstance(agent, dict):
            errors.append(f"Step 4 skipped: subagent {index} is not an object")
            continue
        idea_id = agent.get("idea_id")
        if not isinstance(idea_id, str):
            errors.append(f"Step 4 skipped: subagent {index} is missing idea_id")
            continue
        seen.append(idea_id)
        for field in ("title", "one_liner", "target_user"):
            if not isinstance(agent.get(field), str) or not str(agent.get(field)).strip():
                errors.append(f"Step 4 skipped: {idea_id} contract is missing {field}")
        cited = agent.get("signals")
        if not isinstance(cited, list) or not cited:
            errors.append(f"Step 4 skipped: {idea_id} contract has no signals")
        keywords = agent.get("keywords")
        if (
            not isinstance(keywords, list)
            or not 3 <= len(keywords) <= 6
            or not all(isinstance(item, str) and item.strip() for item in keywords)
        ):
            errors.append(f"Step 4 skipped: {idea_id} needs 3–6 search keywords")
    if seen != idea_ids:
        errors.append("Step 4 skipped: one subagent contract is required per idea")


def check_project(idea_id: str, project: object, errors: list[str]) -> dict | None:
    if not isinstance(project, dict):
        errors.append(f"Step 5 skipped: {idea_id} project is not an object")
        return None
    repo = project.get("repo")
    if not isinstance(repo, str) or repo.count("/") != 1:
        errors.append(f"Step 5 skipped: {idea_id} project repo must be owner/name")
        repo = ""
    url = project.get("url")
    if repo and url != f"https://github.com/{repo}":
        errors.append(f"Step 5 skipped: {idea_id} project URL must be https://github.com/{repo}")
    for field in ("stars", "forks"):
        value = project.get(field)
        if not isinstance(value, int) or isinstance(value, bool) or value < 0:
            errors.append(f"Step 5 skipped: {idea_id} {field} must be a non-negative integer")
    downloads = project.get("downloads")
    if not isinstance(downloads, dict) or downloads.get("source") not in DOWNLOAD_SOURCES:
        errors.append(f"Step 5 skipped: {idea_id} downloads.source must be npm, pypi, docker, release, or none")
    elif downloads.get("source") == "none":
        if downloads.get("value") is not None:
            errors.append(f"Step 5 skipped: {idea_id} has no download source, so value must be null")
    elif not isinstance(downloads.get("value"), int) or isinstance(downloads.get("value"), bool):
        errors.append(f"Step 5 skipped: {idea_id} download value must be an integer or unknown via source none")
    licence = project.get("licence")
    category = project.get("category")
    if not isinstance(licence, str) or not licence.strip():
        errors.append(f"Step 5 skipped: {idea_id} licence is empty; write unknown when it is missing")
        licence = ""
    if category not in CATEGORIES:
        errors.append(f"Step 5 skipped: {idea_id} category is outside the skill")
    elif isinstance(licence, str) and licence:
        expected = expected_category(licence)
        if category == "paid":
            pass
        elif expected is None and category != "source-available":
            errors.append(f"Step 5 skipped: {idea_id} licence {licence} must be source-available or paid")
        elif expected is not None and category != expected:
            errors.append(f"Step 5 skipped: {idea_id} licence {licence} does not match category {category}")
    last_push = project.get("last_push")
    pushed_on = None
    if not isinstance(last_push, str):
        errors.append(f"Step 5 skipped: {idea_id} last_push must be YYYY-MM-DD")
    else:
        try:
            pushed_on = datetime.strptime(last_push, "%Y-%m-%d").date()
        except ValueError:
            errors.append(f"Step 5 skipped: {idea_id} last_push must be YYYY-MM-DD")
    for field in ("archived", "stale", "is_fork"):
        if not isinstance(project.get(field), bool):
            errors.append(f"Step 5 skipped: {idea_id} {field} must be true or false")
    if project.get("archived") is True:
        errors.append(f"Step 5 skipped: {idea_id} kept an archived repo")
    if project.get("is_fork") is True:
        errors.append(f"Step 5 skipped: {idea_id} kept a fork")
    if project.get("fit") not in {"high", "medium"}:
        errors.append(f"Step 5 skipped: {idea_id} kept a project that is not high or medium fit")
    if not isinstance(project.get("why_fit"), str) or not str(project.get("why_fit")).strip():
        errors.append(f"Step 5 skipped: {idea_id} needs why_fit")
    if pushed_on is not None and isinstance(project.get("stale"), bool):
        old = pushed_on < date.today() - timedelta(days=730)
        if old and project.get("stale") is not True:
            errors.append(f"Step 5 skipped: {idea_id} last push is older than 24 months and not flagged stale")
        if not old and project.get("stale") is True:
            errors.append(f"Step 5 skipped: {idea_id} is marked stale but was pushed within 24 months")
    return project if repo else None


def check_github(github: object, idea_ids: list[str], errors: list[str]) -> dict[str, dict]:
    if not isinstance(github, dict):
        errors.append("Step 5 skipped: github must be an object keyed by idea id")
        return {}
    if list(github.keys()) != idea_ids:
        errors.append("Step 5 skipped: every idea needs its own GitHub block and no extra blocks")
    kept: dict[str, dict] = {}
    call_count = 0
    for idea_id in idea_ids:
        block = github.get(idea_id)
        if not isinstance(block, dict):
            errors.append(f"Step 5 skipped: {idea_id} has no GitHub block")
            continue
        if not isinstance(block.get("unverified"), bool):
            errors.append(f"Step 5 skipped: {idea_id} must set unverified true or false")
            continue
        calls = block.get("calls")
        if not isinstance(calls, list):
            errors.append(f"Step 5 skipped: {idea_id} calls must be a list")
            continue
        call_count += len(calls)
        recorded: set[str] = set()
        statuses: list[int] = []
        for call in calls:
            if not isinstance(call, dict):
                errors.append(f"Step 5 skipped: {idea_id} has a call that is not an object")
                continue
            url = call.get("url")
            if not isinstance(url, str):
                errors.append(f"Step 5 skipped: {idea_id} call is missing a URL")
            else:
                reason = explain_search_url(url)
                if reason:
                    errors.append(f"Step 5 skipped: {idea_id} {reason}")
            status = call.get("status")
            if not isinstance(status, int) or isinstance(status, bool):
                errors.append(f"Step 5 skipped: {idea_id} call status must be an integer")
            else:
                statuses.append(status)
            repos = call.get("repos")
            if not isinstance(repos, list) or not all(isinstance(item, str) for item in repos):
                errors.append(f"Step 5 skipped: {idea_id} call repos must be a list of owner/name")
            else:
                recorded.update(repos)
        projects = block.get("projects")
        if not isinstance(projects, list) or len(projects) > 3:
            errors.append(f"Step 5 skipped: {idea_id} must keep at most 3 projects")
            projects = []
        gaps = block.get("gaps")
        if not isinstance(gaps, str) or not gaps.strip():
            errors.append(f"Step 6 skipped: {idea_id} gaps is empty")
        paid = block.get("paid_alternatives")
        if not isinstance(paid, list):
            errors.append(f"Step 6 skipped: {idea_id} paid_alternatives must be a list")
        else:
            for item in paid:
                if not isinstance(item, dict):
                    errors.append(f"Step 6 skipped: {idea_id} paid alternative is not an object")
                    continue
                note = item.get("note")
                name = item.get("name")
                if not isinstance(name, str) or not name.strip():
                    errors.append(f"Step 6 skipped: {idea_id} paid alternative needs a name")
                if not isinstance(note, str) or "not verified via GitHub" not in note:
                    errors.append(f"Step 6 skipped: {idea_id} paid alternative must say not verified via GitHub")
        unverified = block.get("unverified") is True
        if unverified:
            if projects:
                errors.append(f"Step 5 skipped: {idea_id} is GitHub unverified and still names a repo")
            if not calls or not statuses or any(status < 400 and status != 0 for status in statuses):
                errors.append(f"Step 5 skipped: {idea_id} may say GitHub unverified only after a failed API call")
        else:
            if not 2 <= len(calls) <= 4:
                errors.append(f"Step 5 skipped: {idea_id} needs 2–4 GitHub searches")
            parsed: list[dict] = []
            for project in projects:
                checked = check_project(idea_id, project, errors)
                if checked:
                    parsed.append(checked)
            stale_kept = [project for project in parsed if project.get("stale") is True]
            if stale_kept and len(parsed) != 1:
                errors.append(f"Step 5 skipped: {idea_id} may keep a stale repo only when it is the sole project")
            for project in parsed:
                if project["repo"] not in recorded:
                    errors.append(
                        f"Step 5 skipped: {idea_id} repo {project['repo']} was not in this run's API responses"
                    )
            if not projects and not any(status == 200 for status in statuses):
                errors.append(f"Step 5 skipped: {idea_id} has no projects and no successful search")
        kept[idea_id] = block
    if call_count > 12:
        errors.append(f"Step 5 skipped: {call_count} GitHub calls exceeds the limit of 12")
    return kept


def live_errors(github: dict[str, dict], fetch) -> list[str]:
    errors: list[str] = []
    for idea_id, block in github.items():
        if not isinstance(block, dict) or not isinstance(block.get("calls"), list):
            continue
        live_repos: dict[str, dict] = {}
        saw_success = False
        saw_failure = False
        for call in block["calls"]:
            if not isinstance(call, dict) or not isinstance(call.get("url"), str):
                continue
            if explain_search_url(call["url"]):
                continue
            result = fetch(call["url"])
            status = int(result.get("status") or 0)
            if status != call.get("status"):
                errors.append(
                    f"Step 5 skipped: {idea_id} recorded status {call.get('status')} but GitHub returned {status}"
                )
            if status == 200:
                saw_success = True
                live_repos.update(result.get("repos") or {})
            else:
                saw_failure = True
            for name in call.get("repos") or []:
                if status == 200 and name not in (result.get("repos") or {}):
                    errors.append(f"Step 5 skipped: {idea_id} listed {name} but this search did not return it")
        unverified = block.get("unverified") is True
        if unverified and saw_success:
            errors.append(f"Step 5 skipped: {idea_id} GitHub search succeeded, so do not mark it unverified")
        if unverified and not saw_failure:
            errors.append(f"Step 5 skipped: {idea_id} has no failed GitHub call to justify unverified")
        for project in block.get("projects") or []:
            if not isinstance(project, dict):
                continue
            repo = project.get("repo")
            live = live_repos.get(repo)
            if not live:
                errors.append(f"Step 5 skipped: {idea_id} repo {repo} was not returned by GitHub in this run")
                continue
            if not isinstance(live.get("stars"), int) or not isinstance(project.get("stars"), int):
                errors.append(f"Step 5 skipped: {idea_id} stars for {repo} were not copied from GitHub")
            elif abs(live["stars"] - project["stars"]) > 5:
                errors.append(f"Step 5 skipped: {idea_id} stars for {repo} were not copied from GitHub")
            if not isinstance(live.get("forks"), int) or not isinstance(project.get("forks"), int):
                errors.append(f"Step 5 skipped: {idea_id} forks for {repo} were not copied from GitHub")
            elif abs(live["forks"] - project["forks"]) > 5:
                errors.append(f"Step 5 skipped: {idea_id} forks for {repo} were not copied from GitHub")
            if str(project.get("licence") or "").lower() != str(live["licence"]).lower():
                errors.append(f"Step 5 skipped: {idea_id} licence for {repo} was not copied from GitHub")
            if project.get("last_push") != live["last_push"]:
                errors.append(f"Step 5 skipped: {idea_id} last push for {repo} was not copied from GitHub")
            if project.get("archived") is not live["archived"] or project.get("is_fork") is not live["is_fork"]:
                errors.append(f"Step 5 skipped: {idea_id} archived or fork flag for {repo} was not copied from GitHub")
    return errors


def idea_blocks(section: str) -> list[tuple[str, str, re.Match[str]]]:
    lines = section.splitlines()
    starts = [index for index, line in enumerate(lines) if line.startswith("### ")]
    blocks = []
    for position, start in enumerate(starts):
        end = starts[position + 1] if position + 1 < len(starts) else len(lines)
        text = "\n".join(lines[start:end]).strip()
        match = IDEA_HEAD_RE.match(lines[start].strip())
        blocks.append((lines[start], text, match))
    return blocks


def check_markdown(record: dict, score_rows: dict[str, dict], github: dict[str, dict], errors: list[str]) -> None:
    markdown = record.get("output_markdown")
    if not isinstance(markdown, str) or not markdown.strip():
        errors.append("Step 7 skipped: output_markdown is empty")
        return
    chunks, heading_errors = sections(markdown)
    errors.extend(heading_errors)
    if heading_errors:
        return
    summary = str(record.get("summary") or "").strip()
    brief = chunks["## News in brief"]
    if summary and summary not in brief:
        errors.append("Step 7 skipped: News in brief does not contain the summary")
    if len(words(brief_body(brief))) > 120:
        errors.append("Step 7 skipped: News in brief exceeds 120 words")
    for source in record.get("sources") or []:
        if isinstance(source, str) and source not in brief:
            errors.append(f"Step 7 skipped: source {source} is missing from News in brief")
    if record.get("conflicts") and not re.search(r"\bconflict\b", brief, re.I):
        errors.append("Step 7 skipped: conflicts must be flagged in News in brief")
    points = chunks["## Noteworthy points"]
    for signal in record.get("signals") or []:
        if isinstance(signal, dict):
            sid = str(signal.get("id") or "")
            if sid and not has_token(points, sid):
                errors.append(f"Step 7 skipped: noteworthy points omit {sid}")
            if signal.get("type") and str(signal.get("type")) not in points:
                errors.append(f"Step 7 skipped: noteworthy points omit type {signal.get('type')}")
    shortlist = chunks["## Idea shortlist (score table)"]
    for dimension in DIMENSIONS:
        if dimension not in shortlist:
            errors.append(f"Step 3 skipped: score table is missing {dimension}")
    coverage = chunks["## Coverage notes"]
    notes = record.get("coverage_notes")
    if not isinstance(notes, str) or not notes.strip() or notes.strip() not in coverage:
        errors.append("Step 7 skipped: coverage notes are missing")
    ideas = record.get("ideas") or []
    if not ideas:
        reason = str(record.get("empty_reason") or "").strip()
        if not reason or reason not in chunks["## Ideas"]:
            errors.append("Step 7 skipped: Ideas section must include empty_reason")
        if idea_blocks(chunks["## Ideas"]):
            errors.append("Step 7 skipped: empty idea list must not have idea headings")
        return
    blocks = idea_blocks(chunks["## Ideas"])
    if len(blocks) != len(ideas):
        errors.append("Step 7 skipped: idea headings do not match the idea list")
        return
    for idea, (heading, block, match) in zip(ideas, blocks):
        idea_id = idea.get("idea_id")
        if match is None or match.group(1) != idea_id:
            errors.append(f"Step 7 skipped: heading for {idea_id} must be '### {idea_id} — title (score X/40)'")
            continue
        row = score_rows.get(idea_id)
        if row:
            labels = score_labels(score_value(row))
            if match.group(2) not in labels or match.group(3) not in {"40", "40.0"}:
                errors.append(f"Step 3 skipped: {idea_id} heading score does not match the weighted score out of 40")
            if idea_id not in shortlist or not any(label in shortlist for label in labels):
                errors.append(f"Step 3 skipped: score table omits {idea_id}")
        for label in (
            "Idea",
            "Who / problem / why now",
            "Open-source on GitHub",
            "Paid / commercial",
            "Gap",
        ):
            if bullet(block, label) is None:
                errors.append(f"Step 7 skipped: {idea_id} is missing {label}")
        who = bullet(block, "Who / problem / why now") or ""
        if "Signals:" not in who:
            errors.append(f"Step 7 skipped: {idea_id} does not cite signals")
        for sid in idea.get("signals") or []:
            if not has_token(who, sid):
                errors.append(f"Step 7 skipped: {idea_id} omits signal {sid}")
        if idea.get("derisk") and idea["derisk"] not in block:
            errors.append(f"Step 7 skipped: {idea_id} is missing its hurdle tag")
        if idea.get("confidence") and idea["confidence"] not in block:
            errors.append(f"Step 7 skipped: {idea_id} is missing its confidence")
        opened = bullet(block, "Open-source on GitHub") or ""
        named = set(REPO_RE.findall(opened))
        block_github = github.get(idea_id) or {}
        projects = block_github.get("projects") or []
        project_names = [project.get("repo") for project in projects if isinstance(project, dict)]
        if block_github.get("unverified") is True:
            if "GitHub unverified" not in opened or named:
                errors.append(f"Step 5 skipped: {idea_id} must say GitHub unverified and name no repo")
        elif not projects:
            if "none kept" not in opened.lower() or named:
                errors.append(f"Step 5 skipped: {idea_id} kept no repo, so say none kept and name no repo")
        else:
            if project_names[0] not in named or not named.issubset(set(project_names)):
                errors.append(f"Step 5 skipped: {idea_id} open-source line names a repo that was not kept")
            first = projects[0]
            downloads = first.get("downloads") or {}
            if downloads.get("source") == "none" and "popularity proxy, not downloads" not in opened:
                errors.append(f"Step 5 skipped: {idea_id} must label stars as popularity proxy, not downloads")
            for bit in (str(first.get("stars")), str(first.get("licence")), str(first.get("last_push")), str(first.get("fit"))):
                if bit and bit not in opened:
                    errors.append(f"Step 7 skipped: {idea_id} open-source line is missing {bit}")
            if first.get("why_fit") and first["why_fit"] not in opened:
                errors.append(f"Step 7 skipped: {idea_id} open-source line is missing why it fits")
        paid_line = bullet(block, "Paid / commercial") or ""
        paid = block_github.get("paid_alternatives") or []
        if not paid and "none found" not in paid_line.lower():
            errors.append(f"Step 6 skipped: {idea_id} needs a paid option or none found")
        if paid and "not verified via GitHub" not in paid_line:
            errors.append(f"Step 6 skipped: {idea_id} paid line must say not verified via GitHub")
        for item in paid:
            if isinstance(item, dict) and item.get("name") and item["name"] not in paid_line:
                errors.append(f"Step 6 skipped: {idea_id} paid line omits {item['name']}")
        gap_line = bullet(block, "Gap") or ""
        if not gap_line:
            errors.append(f"Step 6 skipped: {idea_id} gap line is empty")
        narrative = " ".join(
            [
                str(idea.get("one_liner") or ""),
                str(idea.get("who_problem_why_now") or ""),
                bullet(block, "Idea") or "",
                who,
            ]
        ).lower()
        for marker in PLAN_MARKERS:
            if marker in narrative:
                errors.append(f"Hard rule skipped: {idea_id} contains plan language ({marker})")
        for marker in UNSAFE:
            if marker in narrative:
                errors.append(f"Hard rule skipped: {idea_id} contains disallowed advice ({marker})")


def check_ideas(record: dict, signal_ids: set[str], score_rows: dict[str, dict], errors: list[str]) -> list[str]:
    ideas = record.get("ideas")
    if not isinstance(ideas, list):
        errors.append("Step 3 skipped: ideas must be a list")
        return []
    if len(ideas) > 5:
        errors.append("Step 3 skipped: more than 5 ideas")
        return []
    if not ideas:
        reason = record.get("empty_reason")
        if not isinstance(reason, str) or not reason.strip():
            errors.append("Step 3 skipped: no ideas and no empty_reason")
        elif not score_rows and "safety" not in reason.lower():
            errors.append("Step 3 skipped: no scores and the empty result is not a safety drop")
        if record.get("subagents") not in ([], None) and record.get("subagents") != []:
            errors.append("Step 4 skipped: empty idea list must have no subagents")
        if record.get("github") not in ({}, None) and record.get("github") != {}:
            errors.append("Step 5 skipped: empty idea list must have no GitHub blocks")
        return []
    idea_ids: list[str] = []
    for index, idea in enumerate(ideas, start=1):
        expected = f"I{index}"
        if not isinstance(idea, dict):
            errors.append(f"Step 6 skipped: idea {index} is not an object")
            idea_ids.append(expected)
            continue
        idea_id = idea.get("idea_id")
        if idea_id != expected:
            errors.append(f"Step 3 skipped: ideas must be numbered {expected} without gaps")
        idea_ids.append(idea_id if isinstance(idea_id, str) else expected)
        for field in ("title", "one_liner", "who_problem_why_now"):
            if not isinstance(idea.get(field), str) or not str(idea.get(field)).strip():
                errors.append(f"Step 6 skipped: {expected} is missing {field}")
        cited = string_list(idea.get("signals"), f"Step 6 skipped: {expected} signals", errors, False)
        if cited:
            unknown = [item for item in cited if item not in signal_ids]
            if unknown:
                errors.append(f"Hard rule skipped: {expected} cites {', '.join(unknown)} with no signal")
        if idea.get("derisk") not in DERISK:
            errors.append(f"Step 6 skipped: {expected} needs a hurdle tag")
        if idea.get("confidence") not in CONFIDENCE:
            errors.append(f"Step 6 skipped: {expected} needs confidence high, medium, or low")
        row = score_rows.get(expected)
        if row and cited and list(row.get("signals") or []) != cited:
            errors.append(f"Step 6 skipped: {expected} signals do not match its score row")
    if record.get("india_first") is True:
        blob = " ".join(
            [
                str(record.get("summary") or ""),
                " ".join(str(idea.get("one_liner") or "") for idea in ideas if isinstance(idea, dict)),
                " ".join(str(idea.get("who_problem_why_now") or "") for idea in ideas if isinstance(idea, dict)),
            ]
        ).lower()
        if not any(marker in blob for marker in INDIA_MARKERS):
            errors.append("Hard rule skipped: India-first framing is missing")
    return [idea_id for idea_id in idea_ids if isinstance(idea_id, str)]


def validate(record: object, fetch=None) -> list[str]:
    """Return skill violations. An empty list means the news item followed the skill."""
    if not isinstance(record, dict):
        return ["skill skipped: run record must be a JSON object"]
    errors: list[str] = []
    missing = [key for key in REQUIRED if key not in record]
    if missing:
        return ["skill skipped: missing " + ", ".join(missing)]
    check_input(record, errors)
    check_summary(record, errors)
    signal_ids = check_signals(record, errors)
    if not isinstance(record.get("ideas"), list) or not isinstance(record.get("scores"), list):
        errors.append("skill skipped: ideas and scores must be lists")
        return errors
    if len(record["ideas"]) > 5:
        errors.append("Step 3 skipped: more than 5 ideas")
        return errors
    score_rows = check_scores(record.get("scores"), signal_ids, errors)
    idea_ids = check_ideas(record, signal_ids, score_rows, errors)
    if record["ideas"] and set(score_rows) != set(idea_ids):
        errors.append("Step 3 skipped: every idea needs a score row")
    if record["ideas"] and idea_ids:
        check_subagents(record.get("subagents"), idea_ids, errors)
        github = check_github(record.get("github"), idea_ids, errors)
        if fetch is not None and github:
            errors.extend(live_errors(github, fetch))
    else:
        github = {}
    check_markdown(record, score_rows, github if record["ideas"] else {}, errors)
    return errors


def main(argv: list[str] | None = None) -> int:
    args = list(sys.argv if argv is None else argv)
    if len(args) != 2:
        print("FAIL")
        print("- usage: python3 validate_flow.py <run.json>")
        return 1
    path = Path(args[1])
    try:
        record = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        print("FAIL")
        print(f"- could not read run record: {exc}")
        return 1
    token = token_from_env()
    errors = validate(record, fetch=lambda url: github_fetch(url, token))
    if errors:
        print("FAIL")
        for item in errors:
            print(f"- {item}")
        return 1
    print("OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
