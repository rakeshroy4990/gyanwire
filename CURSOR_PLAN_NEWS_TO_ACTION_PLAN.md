# CURSOR_PLAN — News → Idea → Action Plan (post-S16)

**Status:** Active addendum after S16. Does not change the LLM model, provider, or temperature.

**Wins on UI and content.** The shipped `com.gyanwire.actionplan` pipeline (resolve → NewsBrief → angles → compose → lint → persist) owns structure, linter, NewsBrief cache, and persistence. Where this file and that pipeline overlap on screens or copy, **this file wins**.

**Not** the S10/S11 business plan (`/api/plans/skill`, weekly outline). Action plans use `/api/action-plans` only.

Agent skill `.cursor/skills/news-to-ideas` still stops at ideas.

---

## Journey

```text
[1 Category feed] → [2 Get an idea] → [3 Make plan] → [4 Plan overview] → [5 Step drawer]
 pack-gated news     IdeasPanel          PlanSheet          PathSwitch          OS | Paid | Mix
```

## Naming lock

| Name | Meaning | Prefix |
|---|---|---|
| Business plan | Skill budget + 12-week + outline (S10–S11) | `/api/plans/*` |
| Action plan | Executable content steps from news | `/api/action-plans` |

## Steps N1–N7

| # | Outcome |
|---|---|
| N1 | Pack YAML + allowlist feed + `NewsRelevanceService` + Why-here UI + hide source |
| N2 | `POST /api/news/{id}/ideas` NewsBrief-first + IdeasPanel + evidence + rating |
| N3 | PlanSheet (format / budget / demo) + overview PathSwitch shell |
| N4 | Micro-step YAML + step drawer API + StepDrawer UI |
| N5 | Paid path + gain % (estimate) + PaidBenefit + GainBadge |
| N6 | `step_timings` + calibration (estimate → measured at n≥5) |
| N7 | Other formats (LinkedIn, YouTube long, carousel, X thread) |

## APIs

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/news/{industry}` | Relevance-gated browse (default) |
| `POST` | `/api/news/{id}/ideas` | Ideas from NewsBrief |
| `POST` | `/api/ideas/{id}/rating` | `useful` / `not_useful` |
| `POST` | `/api/action-plans` | Create from news URL + format/budget/demo |
| `POST` | `/api/ideas/{id}/launch-plan` | Create from saved idea + options |
| `GET` | `/api/action-plans/{id}/steps/{stepKey}` | Step drawer (both tabs) |
| `POST` | `/api/action-plans/{id}/steps/{stepKey}/timing` | Actual minutes |
| `GET` | `/api/action-plans/tools` | Tools by step/format/tier |

## Non-goals

- No new LLM provider, embeddings, tool-calling, or model id changes.
- Do not merge into S10/S11.
- Do not label freemium as open source.
- Do not invent gain % from quality or affiliate.
- Do not pad thin feeds with ungated Google News.
