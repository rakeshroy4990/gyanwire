---
name: news-to-ideas
description: Convert a news link or pasted news text into at most 5 business IDEAS (never plans), each paired with verified GitHub projects tagged open-source or paid. Use whenever the user clicks a news item, supplies news (URL or text), and asks for ideas, opportunities, startups, or "what can be built from this".
---

# News → Business Ideas (Gyanwire)

This file is model-agnostic. Every model (any vendor, any size) must follow it exactly. No model-specific syntax, no model switching inside the flow.

## Hard rules (never break)

1. **Ideas only, never plans.** An idea = problem + who + what it is + why now. No roadmap, budget, hiring, pricing, GTM, timelines, or tech-stack design.
2. **Max 5 ideas.** Fewer is fine. Never pad.
3. **Evidence-linked.** Every idea cites at least one signal (S#) from the news. No signal, no idea.
4. **GitHub data comes from live API calls**, never from memory. If the API fails, say "GitHub unverified" instead of guessing.
5. **Unknown stays unknown.** Missing number, licence, or download count → write `unknown`.
6. **India-first** framing (rupee, Indian regulation, Indian languages, Tier-2/3 reach) unless the news is clearly elsewhere.
7. **No clinical, legal, or financial advice.** Ideas in health/finance are about tooling and services, not diagnosis or investment calls.

## Validation gate (never skip)

Every news item ends with this command. Show ideas only after it prints `OK`. `FAIL` means a step was skipped. Fix the run record and execute it again. Do not answer on `FAIL`.

```bash
python3 .cursor/skills/news-to-ideas/scripts/validate_flow.py run.json
```

Write `run.json` in the shape described in that script. Step 5 searches go through `python3 .cursor/skills/news-to-ideas/scripts/github_search.py`, and the validator calls GitHub again. A repo that was not in this run's API response is `FAIL`. There is no offline switch.

## Input

| Input | Handling |
|---|---|
| URL | Fetch it. If blocked/paywalled/empty (e.g. thehindu.com may block fetchers): search the headline, use 2+ syndicated or official sources (PIB, AIR, agency copy), and say which sources were used. If still thin, ask the user to paste the text. |
| Pasted text | Use as is. |
| Both | Pasted text wins; URL is context. |

Record: `headline`, `source(s)`, `publish date`, `region`, `industry` (map to Gyanwire industries: Share Market, IT, Medical, Space, Social Media, Gaming, Astrology, or `other`).

## Step 0 — FYI gate (skip ideas when the news is only informational)

Before Step 1, classify the item:

| Category | Keep for ideas? | Examples |
|---|---|---|
| `actionable` | Yes | Launch, funding, approval, new rule/deadline, price change, shortage, named compliance change |
| `fyi` | **No — stop** | Market-flow commentary (FII/FPI dumps, "pain ahead", reversal speculation); bureaucratic process chatter (seeks explanation/clarification, lists unapproved items with no new rule); celebrity/status updates |

If category is `fyi`, do **not** invent ideas. Reply with:

```
## News in brief
<≤80 words>
## Category
FYI only — no business idea from this item.
## Why
<one line: no specific buyer or buying moment>
```

Then stop. Do not run Steps 1–7 or the validation gate.

## Step 1 — Summarise (max 120 words)

Plain summary: who did what, where, when, how big, why it matters. Separate **facts** from **quotes/opinions**. If sources disagree on a number (e.g. 58,000 vs other counts), keep both and flag `conflict`.

## Step 2 — Signal tree (the "noteworthy points")

Extract every point that could seed an idea. Each signal can have **sub-signals**. Use stable ids.

```
S1  <signal>                      type: <tag>   evidence: "<short paraphrase>"
 ├─ S1.1 <sub-signal>
 └─ S1.2 <sub-signal>
S2  ...
```

Signal types (tag each): `policy` · `funding/budget` · `scale/number` · `new-org-or-structure` · `tech/digital-tool` · `shortage/gap` · `behaviour/stigma` · `regulation/compliance` · `partnership` · `timeline/deadline` · `risk/failure`.

Rules: one fact per signal; include numbers and named bodies; do not invent; mark `inferred` if not stated in the text.

## Step 3 — Idea candidates → pick ≤ 5

Generate candidates from signals using these lenses:
- **Enable** the people the news names (tools for them)
- **Fill the gap** the news admits (shortage, stigma, last-mile)
- **Ride the money/policy** (vendors, compliance, reporting)
- **Data/analytics** exhaust of the programme
- **Adjacent users** (students, freshers, local businesses, NGOs)

Score each candidate 1–5 on: **Urgency** (×2) · **Market reach in India** (×2) · **Timing window** (×1.5) · **Buildability by a small team** (×1) · **Defensibility** (×1) · **Fresher-friendly** (×0.5). Drop duplicates (same user + same problem). Keep top ≤ 5 with **diversity**: no more than 2 ideas for the same user group. Show the score table briefly.

## Step 4 — One subagent per idea (max 5, run in parallel)

Spawn one subagent per selected idea. Subagents get only their own contract, not each other's work.

**Input contract**
```json
{ "idea_id": "I1", "title": "", "one_liner": "", "signals": ["S1","S2.1"], "target_user": "", "keywords": ["3-6 search terms, include synonyms"] }
```
**Output contract**
```json
{ "idea_id": "I1", "projects": [ { "repo": "owner/name", "url": "", "stars": 0, "forks": 0, "downloads": {"source": "npm|pypi|docker|release|none", "value": null}, "licence": "", "category": "open-source|open-source-copyleft|source-available|paid|unknown", "last_push": "YYYY-MM-DD", "archived": false, "fit": "high|medium|low", "why_fit": "one line" } ], "paid_alternatives": [ { "name": "", "note": "" } ], "gaps": "what was NOT found" }
```
If a subagent cannot be spawned (tool limits), run the same steps sequentially. Never skip Step 5.

## Step 5 — GitHub research (inside each subagent)

1. Run 2–4 repo searches with different keyword angles: `GET https://api.github.com/search/repositories?q=<terms>&sort=stars&order=desc&per_page=10` (add `pushed:>YYYY-MM-DD` for last ~24 months when needed). Use `GITHUB_TOKEN` if present to avoid rate limits. Execute each search with `python3 .cursor/skills/news-to-ideas/scripts/github_search.py "<terms>"`. Copy repositories only from that JSON.
2. **"Best downloads" ladder** (GitHub has no download count for most repos), use the first available, always record which:
   1. Package registry downloads (npm / PyPI weekly) if the repo publishes a package
   2. Docker pulls (if an image is linked)
   3. Release asset download counts
   4. Fallback: stars + forks (label as `popularity proxy, not downloads`)
3. **Filter out**: archived, no push in 24+ months (unless it's the clear category leader, then flag `stale`), forks of other repos, tutorials/awesome-lists/homework repos, fit = low.
4. Keep **top 3** per idea by (fit, then downloads/popularity, then recency).
5. **Categorise** from the licence API field and README:
   - `open-source`: OSI permissive (MIT, Apache-2.0, BSD, ISC…)
   - `open-source-copyleft`: GPL / AGPL / MPL (note obligations for SaaS use)
   - `source-available`: BSL, SSPL, Elastic, custom, `NOASSERTION`/none → "check before use"
   - `paid`: repo is an SDK/client/sample for a commercial service, or README shows pricing/enterprise tier. Also list known **commercial alternatives** under `paid_alternatives`, marked `not verified via GitHub`.
6. Never recommend a repo you did not see in an API response this run.

## Step 6 — Combine

For each idea, merge: idea + best open-source project + best paid option + gaps. The idea stays an idea; the repo is evidence that the idea is buildable/buyable, not a build plan.

## Step 7 — Output format (exact)

```
## News in brief
<≤120 words> · Sources: <list> · Conflicts: <if any>

## Noteworthy points
<signal tree>

## Idea shortlist (score table)

## Ideas
### I1 — <title>   (score X/Y)
- **Idea:** <one_liner>
- **Who / problem / why now:** <1–2 lines> · Signals: S1, S2.1
- **Open-source on GitHub:** repo · ★stars · downloads(source) · licence · last push · fit — why
- **Paid / commercial:** name — note (or "none found")
- **Gap:** <what isn't covered by either>
(repeat for I2…I5)

## Coverage notes
<blocked sources, GitHub rate-limit, unknown fields>
```
No next-steps section. No plan. If the user then asks "make a plan for idea N", that is a separate request.

Then execute the validation gate. This format is shown to the user only after `OK`.

## Extra points that make the algorithm stronger

1. **Source quality gate:** prefer primary (PIB, regulator, company filing) over aggregators; cross-check key numbers across 2 sources.
2. **Claim vs opinion split** so ministers' quotes never become "market data".
3. **Freshness / timing signal:** deadlines, phase rollouts, tenders and budget cycles raise the Timing score.
4. **Second-order thinking:** for each signal also ask "who gets more work, money, or pain because of this?" (vendors, trainers, compliance, data).
5. **Negative signals:** risks and failures in the news are idea seeds (fraud, drop-outs, grievance, misinformation).
6. **Dedup + diversity rule** (above) to stop 5 variations of one idea.
7. **Profile personalisation (optional, Gyanwire user profile):** if a profile exists, re-weight Buildability and Fresher-friendly by user skills, city tier, budget band, and goal; never change the facts.
8. **Idea de-risking tag** per idea: `regulated` · `needs-govt-data` · `needs-field-ops` · `pure-software`, so users see the hurdle type, still without a plan.
9. **Evidence audit line:** each idea lists signal ids, so any claim is traceable.
10. **Cache + rate-limit:** cache GitHub results per keyword for 24h; max 5 subagents; max ~12 GitHub calls per run; back off on 403/429.
11. **Confidence label** (`high/medium/low`) per idea, based on source count and GitHub fit.
12. **Stable re-run:** same news + same date → same shortlist (use the score table, not model mood).
13. **Safety filter:** drop ideas that exploit vulnerable people, enable surveillance/scams, or need medical-diagnosis claims.
14. **Language/regional hook:** flag ideas where Indian languages or Tier-2/3 reach is the real edge.

## Worked example (shape only)

News: *Union Health Minister launches JOSH Teams to accelerate TB elimination* (Oct 2026).

Signals (abridged):
- S1 JOSH Teams: 5 MY Bharat volunteers + 5 NCC cadets per team, led by a Class-I officer (`new-org-or-structure`)
- S2 55 districts in 11 states/UTs in phase 1, nationwide expansion envisaged (`scale`, `timeline`)
- S3 Digital tools: reporting app, Khushi Avatar e-Sangini, flipbook (`tech/digital-tool`)
- S4 Myths and stigma are a named barrier (`behaviour/stigma`)
- S5 Minister: strategy must cover the full continuum of care, not just awareness/screening (`shortage/gap`)
- S6 67,933 Panchayats recognised TB Mukt (`scale`); volunteer count differs by source (58,000 quoted in one report) → `conflict`

Idea shortlist (illustrative):
1. Field-reporting and coordination layer for volunteer health squads (S1, S3). Live check found `medic/cht-core` (AGPL-3.0, ~557★, pushed Oct 2026) as a strong open-source base; `rubyforgood/casa` (MIT) for volunteer case management. Paid: commercial community-health platforms (not verified via GitHub).
2. Multilingual TB myth-busting assistant over WhatsApp/IVR (S3, S4). Runtime search needed; initial search surfaced only low-star health chatbot repos, so report `gap`.
3. Treatment-completion reminders and follow-up for the "continuum of care" (S5).
4. Youth volunteer training and verifiable impact credentials for students (S1, fresher-friendly).
5. Panchayat-level TB-Mukt progress dashboard for local officials (S6).

(The GitHub rows above are from a sample search; a real run must re-query and cite fresh numbers.)
