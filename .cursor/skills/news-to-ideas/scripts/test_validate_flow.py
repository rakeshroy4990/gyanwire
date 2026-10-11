"""The news-to-ideas gate rejects a run that skipped a skill step."""

from __future__ import annotations

import json
import os
import sys
import tempfile
import unittest
from copy import deepcopy
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import check_skill_bound  # noqa: E402
import github_api  # noqa: E402
import validate_flow  # noqa: E402

SUMMARY = (
    "In October 2026 the Union Health Minister launched JOSH Teams in India. "
    "Each team pairs MY Bharat volunteers with NCC cadets under a Class-I officer. "
    "Phase 1 covers 55 districts. The launch and the district count are facts. "
    "The minister's remark on continuum of care is an opinion."
)
LIVE = {
    "stars": 557,
    "forks": 10,
    "licence": "AGPL-3.0",
    "last_push": "2026-10-01",
    "archived": False,
    "is_fork": False,
}


def search(term: str) -> str:
    return (
        "https://api.github.com/search/repositories"
        f"?q={term}&sort=stars&order=desc&per_page=10"
    )


def stub_fetch(url: str, token: str | None = None) -> dict:
    del token
    if "rate-limit" in url:
        return {"status": 403, "repos": {}, "error": "rate limit"}
    return {"status": 200, "repos": {"medic/cht-core": dict(LIVE)}}


def project() -> dict:
    return {
        "repo": "medic/cht-core",
        "url": "https://github.com/medic/cht-core",
        "stars": 557,
        "forks": 10,
        "downloads": {"source": "none", "value": None},
        "licence": "AGPL-3.0",
        "category": "open-source-copyleft",
        "last_push": "2026-10-01",
        "archived": False,
        "stale": False,
        "is_fork": False,
        "fit": "high",
        "why_fit": "field health reporting",
    }


def idea_bundle(index: int, user: str) -> dict:
    idea_id = f"I{index}"
    title = f"Field reporting {index}"
    one_liner = f"A shared reporting layer {index} for volunteer health squads in India."
    who = f"Squad leads in India need one report while phase 1 is live. Case {index}."
    return {
        "score": {
            "idea_id": idea_id,
            "title": title,
            "one_liner": one_liner,
            "target_user": user,
            "problem": f"squads lack a shared report {index}",
            "signals": ["S1"],
            "urgency": 4,
            "market_reach": 4,
            "timing": 4,
            "buildability": 3,
            "defensibility": 2,
            "fresher_friendly": 3,
        },
        "idea": {
            "idea_id": idea_id,
            "title": title,
            "one_liner": one_liner,
            "who_problem_why_now": who,
            "signals": ["S1"],
            "derisk": "needs-field-ops",
            "confidence": "medium",
        },
        "subagent": {
            "idea_id": idea_id,
            "title": title,
            "one_liner": one_liner,
            "signals": ["S1"],
            "target_user": user,
            "keywords": ["community health toolkit", "volunteer case management", "cht-core"],
        },
        "github": {
            "unverified": False,
            "calls": [
                {"url": search(f"cht{index}a"), "status": 200, "repos": ["medic/cht-core"]},
                {"url": search(f"cht{index}b"), "status": 200, "repos": ["medic/cht-core"]},
            ],
            "projects": [project()],
            "paid_alternatives": [
                {
                    "name": "Commercial community-health platforms",
                    "note": "not verified via GitHub",
                }
            ],
            "gaps": "No offline Hindi pack in the repos found.",
        },
    }


def render(record: dict) -> str:
    signal_lines = []
    for signal in record["signals"]:
        signal_lines.append(
            f"{signal['id']}  {signal['text']}  type: {signal['type']}  evidence: \"{signal['evidence']}\""
        )
    score_lines = [
        "| Idea | Urgency | Market reach | Timing | Buildability | Defensibility | Fresher-friendly | Score |"
    ]
    idea_blocks = []
    for row, idea in zip(record["scores"], record["ideas"]):
        total = validate_flow.score_value(row)
        label = f"{total:.1f}".removesuffix(".0")
        score_lines.append(
            f"| {row['idea_id']} | {row['urgency']} | {row['market_reach']} | {row['timing']} | "
            f"{row['buildability']} | {row['defensibility']} | {row['fresher_friendly']} | {label}/40 |"
        )
        opened = (
            "medic/cht-core · ★557 · downloads(none, popularity proxy, not downloads) · "
            "AGPL-3.0 · 2026-10-01 · high — field health reporting"
        )
        github = record["github"][idea["idea_id"]]
        if github["unverified"]:
            opened = "GitHub unverified"
        elif not github["projects"]:
            opened = "none kept"
        idea_blocks.append(
            "\n".join(
                [
                    f"### {idea['idea_id']} — {idea['title']}   (score {label}/40)",
                    f"- **Idea:** {idea['one_liner']}",
                    (
                        f"- **Who / problem / why now:** {idea['who_problem_why_now']} · "
                        f"Signals: {', '.join(idea['signals'])} · {idea['derisk']} · {idea['confidence']}"
                    ),
                    f"- **Open-source on GitHub:** {opened}",
                    "- **Paid / commercial:** Commercial community-health platforms — not verified via GitHub",
                    f"- **Gap:** {github['gaps']}",
                ]
            )
        )
    ideas_text = "\n\n".join(idea_blocks) if idea_blocks else str(record.get("empty_reason") or "")
    sources = ", ".join(record["sources"])
    conflict = "conflict" if record["conflicts"] else "none"
    return "\n".join(
        [
            "## News in brief",
            record["summary"],
            f"· Sources: {sources} · Conflicts: {conflict}",
            "",
            "## Noteworthy points",
            "\n".join(signal_lines),
            "",
            "## Idea shortlist (score table)",
            "\n".join(score_lines),
            "",
            "## Ideas",
            ideas_text,
            "",
            "## Coverage notes",
            record["coverage_notes"],
            "",
        ]
    )


def build(count: int = 1, same_user: bool = False) -> dict:
    bundles = [
        idea_bundle(index, "volunteer squad leads" if same_user or index == 1 else f"user group {index}")
        for index in range(1, count + 1)
    ]
    record = {
        "headline": "Union Health Minister launches JOSH Teams",
        "sources": ["PIB"],
        "publish_date": "2026-10-01",
        "region": "India",
        "industry": "Medical",
        "india_first": True,
        "summary": SUMMARY,
        "facts": ["launched JOSH Teams in India", "Phase 1 covers 55 districts"],
        "opinions": ["The minister's remark on continuum of care is an opinion."],
        "conflicts": [],
        "signals": [
            {
                "id": "S1",
                "text": "JOSH Teams pair volunteers and cadets",
                "type": "new-org-or-structure",
                "evidence": "5 MY Bharat volunteers and 5 NCC cadets",
                "inferred": False,
            }
        ],
        "scores": [item["score"] for item in bundles],
        "subagents": [item["subagent"] for item in bundles],
        "github": {item["idea"]["idea_id"]: item["github"] for item in bundles},
        "ideas": [item["idea"] for item in bundles],
        "coverage_notes": "PIB was the source. GitHub searches returned medic/cht-core.",
    }
    record["output_markdown"] = render(record)
    return record


class ValidateFlowTest(unittest.TestCase):
    def test_valid_run_passes_without_network(self):
        self.assertEqual(validate_flow.validate(build()), [])

    def test_valid_run_passes_live_stub(self):
        self.assertEqual(validate_flow.validate(build(), fetch=stub_fetch), [])

    def test_missing_signals_fail(self):
        record = build()
        record["signals"] = []
        record["output_markdown"] = render(record)
        errors = validate_flow.validate(record)
        self.assertTrue(any("Step 2 skipped: no signals" in item for item in errors))

    def test_sixth_idea_fails(self):
        record = build()
        record["ideas"] = record["ideas"] * 6
        errors = validate_flow.validate(record)
        self.assertIn("Step 3 skipped: more than 5 ideas", errors)

    def test_repo_missing_from_recorded_calls_fails(self):
        record = build()
        for call in record["github"]["I1"]["calls"]:
            call["repos"] = []
        errors = validate_flow.validate(record)
        self.assertTrue(any("was not in this run's API responses" in item for item in errors))

    def test_repo_missing_from_live_response_fails(self):
        record = build()
        record["github"]["I1"]["projects"][0]["repo"] = "evil/thing"
        record["github"]["I1"]["projects"][0]["url"] = "https://github.com/evil/thing"
        for call in record["github"]["I1"]["calls"]:
            call["repos"] = ["evil/thing"]
        record["output_markdown"] = render(record).replace("medic/cht-core", "evil/thing")
        errors = validate_flow.validate(record, fetch=stub_fetch)
        self.assertTrue(any("evil/thing" in item for item in errors))

    def test_live_star_mismatch_fails(self):
        record = build()
        record["github"]["I1"]["projects"][0]["stars"] = 1
        record["output_markdown"] = render(record).replace("★557", "★1")
        errors = validate_flow.validate(record, fetch=stub_fetch)
        self.assertTrue(any("stars" in item for item in errors))

    def test_plan_language_fails(self):
        record = build()
        record["ideas"][0]["one_liner"] += " This is a roadmap."
        record["output_markdown"] = render(record)
        errors = validate_flow.validate(record)
        self.assertTrue(any("plan language" in item for item in errors))

    def test_long_summary_fails(self):
        record = build()
        record["summary"] = " ".join(["word"] * 121)
        record["facts"] = ["word"]
        record["opinions"] = []
        record["output_markdown"] = render(record)
        errors = validate_flow.validate(record)
        self.assertTrue(any("maximum is 120" in item for item in errors))

    def test_unknown_signal_fails(self):
        record = build()
        record["ideas"][0]["signals"] = ["S9"]
        record["scores"][0]["signals"] = ["S9"]
        record["output_markdown"] = render(record)
        errors = validate_flow.validate(record)
        self.assertTrue(any("S9" in item for item in errors))

    def test_three_ideas_for_one_user_fail(self):
        errors = validate_flow.validate(build(3, same_user=True))
        self.assertTrue(any("more than 2 ideas for user group" in item for item in errors))

    def test_two_user_groups_pass(self):
        self.assertEqual(validate_flow.validate(build(2)), [])

    def test_unverified_after_api_failure_passes(self):
        record = build()
        record["github"]["I1"] = {
            "unverified": True,
            "calls": [
                {"url": search("rate-limit"), "status": 403, "repos": []},
            ],
            "projects": [],
            "paid_alternatives": [],
            "gaps": "GitHub returned 403, so no repo was checked.",
        }
        record["output_markdown"] = render(record).replace(
            "- **Paid / commercial:** Commercial community-health platforms — not verified via GitHub",
            "- **Paid / commercial:** none found",
        )
        self.assertEqual(validate_flow.validate(record, fetch=stub_fetch), [])

    def test_unverified_when_api_works_fails(self):
        record = build()
        record["github"]["I1"]["unverified"] = True
        record["github"]["I1"]["projects"] = []
        record["output_markdown"] = render(record)
        errors = validate_flow.validate(record, fetch=stub_fetch)
        self.assertTrue(any("do not mark it unverified" in item for item in errors))

    def test_missing_coverage_heading_fails(self):
        record = build()
        record["output_markdown"] = record["output_markdown"].replace("## Coverage notes", "## Notes")
        errors = validate_flow.validate(record)
        self.assertTrue(any("Step 7 skipped" in item for item in errors))

    def test_next_steps_section_fails(self):
        record = build()
        record["output_markdown"] += "## Next steps\nShip a roadmap.\n"
        errors = validate_flow.validate(record)
        self.assertTrue(any("Step 7 skipped" in item for item in errors))

    def test_short_keyword_list_fails(self):
        record = build()
        record["subagents"][0]["keywords"] = ["one", "two"]
        errors = validate_flow.validate(record)
        self.assertTrue(any("3–6 search keywords" in item or "3-6 search keywords" in item for item in errors))

    def test_archived_repo_fails(self):
        record = build()
        record["github"]["I1"]["projects"][0]["archived"] = True
        errors = validate_flow.validate(record)
        self.assertTrue(any("archived" in item for item in errors))

    def test_low_fit_fails(self):
        record = build()
        record["github"]["I1"]["projects"][0]["fit"] = "low"
        record["output_markdown"] = record["output_markdown"].replace("· high —", "· low —")
        errors = validate_flow.validate(record)
        self.assertTrue(any("not high or medium fit" in item for item in errors))

    def test_copyleft_marked_permissive_fails(self):
        record = build()
        record["github"]["I1"]["projects"][0]["category"] = "open-source"
        errors = validate_flow.validate(record)
        self.assertTrue(any("does not match category" in item for item in errors))

    def test_child_signal_without_parent_fails(self):
        record = build()
        record["signals"] = [
            {
                "id": "S1.1",
                "text": "Cadets are named in the team",
                "type": "new-org-or-structure",
                "evidence": "5 NCC cadets",
                "inferred": False,
            }
        ]
        record["ideas"][0]["signals"] = ["S1.1"]
        record["scores"][0]["signals"] = ["S1.1"]
        record["subagents"][0]["signals"] = ["S1.1"]
        record["output_markdown"] = render(record)
        errors = validate_flow.validate(record)
        self.assertTrue(any("no parent signal" in item for item in errors))

    def test_empty_without_reason_fails(self):
        record = build()
        record["ideas"] = []
        record["scores"] = []
        record["subagents"] = []
        record["github"] = {}
        record["empty_reason"] = ""
        record["output_markdown"] = render(record)
        errors = validate_flow.validate(record)
        self.assertTrue(any("no ideas and no empty_reason" in item for item in errors))

    def test_safety_drop_with_no_ideas_passes(self):
        record = build()
        record["ideas"] = []
        record["scores"] = []
        record["subagents"] = []
        record["github"] = {}
        record["empty_reason"] = "safety filter dropped every candidate"
        record["output_markdown"] = render(record)
        self.assertEqual(validate_flow.validate(record), [])

    def test_outside_india_needs_a_reason(self):
        record = build()
        record["india_first"] = False
        errors = validate_flow.validate(record)
        self.assertTrue(any("elsewhere_reason" in item for item in errors))

    def test_search_url_matches_the_skill(self):
        url = github_api.search_url("community health toolkit", "2024-10-11")
        self.assertIsNone(github_api.explain_search_url(url))
        self.assertIn("pushed%3A%3E2024-10-11", url)
        self.assertIsNotNone(github_api.explain_search_url("https://api.github.com/search/repositories?q=cht"))

    def test_cli_prints_ok_for_a_live_stub(self):
        record = build()
        handle = tempfile.NamedTemporaryFile("w", suffix=".json", delete=False, encoding="utf-8")
        json.dump(record, handle)
        handle.close()
        original = validate_flow.github_fetch
        validate_flow.github_fetch = stub_fetch
        try:
            code = validate_flow.main(["validate_flow.py", handle.name])
        finally:
            validate_flow.github_fetch = original
            os.unlink(handle.name)
        self.assertEqual(code, 0)


class SkillBoundTest(unittest.TestCase):
    def test_news_cannot_skip_the_skill_binding(self):
        self.assertEqual(check_skill_bound.binding_errors(), [])


if __name__ == "__main__":
    unittest.main()
