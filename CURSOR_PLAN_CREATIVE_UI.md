# Gyanwire — Plan to finish: Creative UI + What-If Simulator

Purpose: make `/plan` feel like a trail you can walk and re-gear, then let users swap tools/models per week and see **estimated** cost and efficiency ranges until they pick a fit.

Related: `BRIEF.md` (forest / bone / amber), `CURSOR_PLAN_FLOW_FIRST.md` S10–S11, `.cursor/rules/gyanwire.mdc`. MODEL FREEZE still applies.

Decisions locked for this plan:

| Decision | Choice |
|---|---|
| Creative shell | Build **Trail map → Gear pack → Fuel gauge → Week sheet** first; What-If layers on top |
| Options catalog | **Extend V12** `tools` / `courses` (no parallel `options_catalog` table) |
| Currency | `Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 })` |
| Honesty | Every efficiency figure is a **range** + confidence badge; placeholders never ship as facts |

How to use: one step per chat. Paste the step block from §9.

---

## 0. Honesty rule

"How much efficiency it will increase" cannot be known exactly.

- Show a **range** (e.g. "saves about 2–4 hrs over the plan"), never a single precise figure.
- Label every number "estimate"; assumptions one tap away ("How is this calculated?").
- Seed multipliers are **placeholders** until measured (What-If Step 8).
- If a paid option shows little or no gain for a task, say so. A simulator that always says "upgrade" has no value.

---

## 1. What exists today (do not rebuild blindly)

| Piece | Location | Note |
|---|---|---|
| Plan page | [`client/src/pages/PlanPage.vue`](client/src/pages/PlanPage.vue) | Finish anim, tool buckets, vertical week timeline |
| Finish anim | [`client/src/components/plan/PlanFinishAnim.vue`](client/src/components/plan/PlanFinishAnim.vue) | Budget ring + week chips — seed for Fuel gauge |
| Plan styles | [`client/src/styles.css`](client/src/styles.css) (~1863+) | Extract plan tokens into `plan.css` |
| Skill API | `POST /api/plans/skill` via [`useProduct.js`](client/src/composables/useProduct.js) | Weeks have `toolId` / `toolName` / costs; **no `taskType`** |
| Planner | [`SkillBudgetPlanner.java`](gyanwire-server/src/main/java/com/gyanwire/plans/SkillBudgetPlanner.java) | Phases `learn` → `build` → `prove` → `decide`; sittings = `max(1, hoursPerWeek/5)` only in task copy |
| Catalog | V12 `tools` / `courses` via [`FlowStore.catalogItems()`](gyanwire-server/src/main/java/com/gyanwire/persistence/FlowStore.java) | Has `weeks_saved`, `free_alternative_id`, `last_verified_at`, `affiliate=false` |
| IndexedDB pattern | [`industries.service.js`](client/src/services/industries.service.js) + [`indexedDb.js`](client/src/services/indexedDb.js) | Reuse for catalog + variants |
| Pro / 402 | `PlanLimitException` + `can_roadmap` | Reuse for multi-variant gate |
| Share poster | **None** | Minimal share card built in What-If Step 9 |
| Client unit tests | **None** (Vitest not installed) | Add Vitest only for pure simulator calc |

Trail map / Gear pack / Fuel gauge / Week sheet / What-If: **not in repo**. This document is the source of truth.

---

## 2. Visual language (BRIEF-aligned)

Calm tool surface: forest track, bone type, amber efficiency band.

- Tokens live in [`client/src/styles/plan.css`](client/src/styles/plan.css) (imported from `styles.css`); reuse `--ink`, `--accent`, `--good`, Fraunces / DM Sans.
- Efficiency band: amber range bar on a forest track; unlit portion = uncertainty.
- Motion: trail draw-in, node select, meter fill — respect `prefers-reduced-motion` (instant final state).
- No affiliate/sponsored ordering. Order: free first, then best estimated value.
- Test at **375px** and **1280px**.

---

## 3. Core concepts

| Term | Meaning |
|---|---|
| **Task type** | What a week mostly involves: `research`, `writing`, `coding`, `design`, `data`, `learning`, `outreach` |
| **Option** | A row from `tools` ∪ `courses` a user can pick for a week |
| **Baseline** | The free (or planned) option the skill plan started with for that week |
| **Efficiency** | Estimated hours saved vs baseline as `[lo, hi]`, plus optional quality note (non-numeric) |
| **Value of time** | Optional ₹/hour for payback maths (default off) |
| **Variant** | Named week→option map (A / B / C) the user can compare |

### Task-type mapping (server, deterministic)

Weeks do not store `taskType` today. Derive and **return it on the skill-plan week payload**:

| Source | Rule |
|---|---|
| Phase `learn` | `learning` (override if tool skills are strongly writing/research) |
| Phase `build` | From tool `unlocks_skills` / category: `python|ide|code` → `coding`; `design` → `design`; `sheets|ops|analytics` → `data`; `writing` → `writing`; else `coding` |
| Phase `prove` | `outreach` |
| Phase `decide` | `learning` |
| Unknown | `learning`; simulator falls back to speedup `[1,1]` |

Also expose on each week: `sittings`, `hoursPerWeek` (from profile), `baseHours = sittings * hoursPerSitting` with `hoursPerSitting = 5` (matches current sittings formula inverse). Document the constant in Assumptions popover.

---

## 4. Data model (extend V12)

### Migration `V19__plan_option_fit.sql`

Add to **both** `tools` and `courses`:

```sql
kind            TEXT NOT NULL DEFAULT 'tool',  -- tool | model | course
task_fit        JSONB NOT NULL DEFAULT '{}',   -- { "coding": { "speedup": [1.0,1.2], "confidence": "placeholder", "source": "..." }, ... }
priced_at       DATE,                          -- nullable; UI uses COALESCE(priced_at, last_verified_at)
sample_count    INTEGER NOT NULL DEFAULT 0     -- filled by calibration (Step 8)
```

Seed: 3–4 swappable options per major task type with `confidence: "placeholder"`. Prefer existing IDs (`vscode`, `chatgpt-free`, `canva-pro`, `notion-plus`, `yt-python`, `paid-python`, …). Free baselines stay `speedup: [1.0, 1.0]`, `confidence: "baseline"`.

Do **not** invent precise marketed claims. Placeholders may be modest ranges (e.g. `[1.05, 1.25]`) and must render as "placeholder" in UI.

API shape (GET `/api/options`, cached like industries):

```json
{
  "id": "vscode",
  "name": "VS Code",
  "kind": "tool",
  "monthlyInr": 0,
  "billing": "free",
  "pricedAt": "2026-10-09",
  "stale": false,
  "taskFit": {
    "coding": { "speedup": [1.0, 1.0], "confidence": "baseline", "sampleCount": 0 }
  },
  "freeAlternativeId": null,
  "bucket": "tools"
}
```

`monthlyInr`: for `billing=monthly` use `cost_inr`; for `once` amortize over plan months in the calculator (not in the catalog row); for `free` use 0.

### Per-user variants (IndexedDB first)

```js
planVariant = {
  id, name, createdAt, ideaId,
  weekOptions: { 1: 'python-free', 2: 'paid-x', ... },
  hourlyValueInr: null
}
```

Server sync of variants is out of scope until after Step 8; IndexedDB is the store.

### Actuals (Step 8)

```sql
-- V20__plan_actuals.sql
plan_week_actuals (
  id, user_id, idea_id, week_no, option_id, task_type,
  hours_actual NUMERIC NOT NULL,
  created_at
)
-- RLS: user_id = auth user only; aggregates for calibration never return raw rows to other users
```

---

## 5. Calculation (`usePlanSimulator.js`)

Pure functions + thin reactive wrapper. No hard-coded multipliers in components.

Per week:

```text
baseHours      = sittings * hoursPerSitting
speedup        = option.taskFit[weekTaskType].speedup // [lo, hi], else [1,1]
hoursRange     = [baseHours / hi, baseHours / lo]
savedRange     = [baseHours - hoursRange[1], baseHours - hoursRange[0]]
```

Plan totals:

```text
costInr            = unique options priced once per month the plan spans (match PlanPage copy)
savedHours         = sum weekly savedRange as [lo, hi]
weeksShortened     = savedHours / weeklyCapacityHours   // optional display
costPerHourSaved   = costInr / savedHours               // range; guard ÷0
paybackInr         = savedHours * hourlyValueInr - costInr  // only if hourlyValueInr set
```

Guards:

- `hi == lo == 1` → "No estimated change for this task."
- Cost up and saved-hours low ≈ 0 → "Not clearly worth it"
- Stale price (`pricedAt` / `last_verified_at` older than 30 days, or `stale=true`) → "check current price"
- Never show a single-point efficiency number

Unit tests (Vitest, calculator only): free→free, free→paid known multipliers, mixed variants, unique-tool pricing, missing task types, "not worth it".

---

## 6. UI concepts

### Creative shell (build first)

1. **Trail map** — horizontal (scroll on mobile) week path replaces the vertical `ol.plan-timeline`. Nodes are tappable.
2. **Gear pack** — "Tools in this plan" becomes token chips by bucket (same data as today's `lines[]`).
3. **Fuel gauge** — refactor `PlanFinishAnim` budget ring into a persistent Fuel gauge (₹ spent vs skill budget). Keep the one-shot finish celebration as an optional intro.
4. **Week sheet** — bottom sheet / side panel on node tap: goal, tasks, metric, tool token. Slot for Swap (What-If Step 3).

### What-If layer

5. **Swap handle** — on tool token in Week sheet.
6. **Option drawer** — options for that task type: name, price, efficiency band, confidence badge; free first.
7. **Ghost trail** — baseline path dashed under the active variant; affected weeks get "−Nh" chips (range midpoint for chip label only if lo≈hi within 0.5h; else show "−2–4h").
8. **Efficiency meter** — beside Fuel gauge: hours-saved range, cost, cost/hour saved, Assumptions popover.
9. **Variant tabs A/B/C** + compare table.
10. **Best-value nudge** — one suggestion, never auto-applied; may say "no worthwhile upgrade".
11. **Value-of-time** — optional ₹/hour → payback / doesn't / unclear.

---

## 7. File layout

```text
CURSOR_PLAN_CREATIVE_UI.md          # this file

client/src/
  styles/plan.css
  components/plan/
    PlanFinishAnim.vue              # keep intro; slim if needed
    TrailMap.vue
    GearPack.vue
    FuelGauge.vue
    WeekSheet.vue
    whatif/
      SwapDrawer.vue
      OptionCard.vue
      EfficiencyMeter.vue
      VariantTabs.vue
      CompareTable.vue
      GhostTrail.vue
      AssumptionsPopover.vue
      BestValueNudge.vue
      ValueOfTimeInput.vue
      ShareVariantCard.vue          # Step 9
  composables/
    usePlanView.js                  # trail/gear/fuel wiring from skill plan
    usePlanSimulator.js
    usePlanVariants.js
    useOptionCatalog.js
  services/
    optionCatalog.service.js        # GET /api/options, IndexedDB
    planVariants.service.js         # IndexedDB only until later sync
    planActuals.service.js          # Step 8
  composables/__tests__/            # Vitest: simulator pure fns
    usePlanSimulator.spec.js

gyanwire-server/
  src/main/java/com/gyanwire/plans/
    OptionCatalogController.java    # GET /api/options?taskType=
    OptionCatalogService.java
    WeekTaskType.java               # phase/skills → taskType
    CalibrationController.java      # POST /api/plan/actuals (Step 8)
    CalibrationService.java
  db/migration/
    V19__plan_option_fit.sql
    V20__plan_actuals.sql           # Step 8
    V21__plan_variant_limits.sql    # Step 9: max_plan_variants on plans
```

Layering: components → composables → services. No API calls in components.

---

## 8. Steps

### C0 — Tokens and doc wiring

- Add `client/src/styles/plan.css` with plan-specific tokens; import from `styles.css`.
- Move plan-page selectors gradually; do not restyle research.
- Link this file from `CURSOR_PLAN_FLOW_FIRST.md` (one line under S10/S11).
- Done when: plan page unchanged visually, tokens load, reduced-motion rules still apply.

### C1 — Trail map

- Replace vertical timeline in `PlanPage.vue` with `TrailMap.vue` fed by `plan.weeks`.
- Nodes show weekNo; selected node opens `WeekSheet`.
- Done when: 375px scrolls horizontally; 1280px shows full path; keyboard focus works on nodes.

### C2 — Gear pack

- Replace bucket lists with `GearPack.vue` token chips (same `lines[]` data).
- Done when: each tool still shows name + ₹ + billing; no card chrome beyond interaction affordance.

### C3 — Fuel gauge + Week sheet

- Extract persistent `FuelGauge.vue` from `PlanFinishAnim` ring maths; keep finish anim as entrance.
- Ship `WeekSheet.vue` with tool token (no Swap yet).
- Done when: budget/spent readable without the anim; sheet opens/closes with reduced-motion support.

### Step 1 — Catalog + calculator (no What-If UI)

- `V19__plan_option_fit.sql` + seed placeholders.
- `GET /api/options` (permitAll or authenticated — match `/api/industries` pattern).
- Skill plan weeks include `taskType`, `sittings`, `baseHours`.
- `optionCatalog.service.js` + IndexedDB store (bump `DB_VERSION`).
- `usePlanSimulator.js` + Vitest cases.
- Done when: calculator never returns a single-point estimate; Java test for taskType mapping; GET options returns extended rows.

### Step 2 — Variants state

- `usePlanVariants.js` + `planVariants.service.js`.
- Auto-create Variant A from skill-plan week tool ids.
- Done when: persist across refresh; duplicate / rename / delete work; free user can hold one variant.

### Step 3 — Swap drawer

- Swap on Week sheet tool token → `SwapDrawer` / `OptionCard`.
- Selecting an option updates active variant + token instantly.
- Done when: only options with a `taskFit` entry for that task type (or free baseline) appear; free first.

### Step 4 — Efficiency meter

- `EfficiencyMeter` beside Fuel gauge; `AssumptionsPopover` lists multipliers used.
- Done when: every number on screen traces to an assumption in the popover; "estimate" labels visible.

### Step 5 — Ghost trail

- `GhostTrail` overlay on Trail map; "−Nh" / "−N–Mh" chips on changed weeks.
- Done when: swapping one week changes only that week’s segment + totals.

### Step 6 — Compare variants

- `VariantTabs` + `CompareTable` (cost, hours saved range, weeks shortened, confidence).
- Done when: A/B/C compare stacks on 375px, columns on 1280px. (Gate enforcement in Step 9; until then allow local A/B/C for build.)

### Step 7 — Value of time + best-value nudge

- Optional ₹/hour; payback states; single nudge with reason.
- Done when: nudge never mutates the plan without a tap; can show "no worthwhile upgrade".

### Step 8 — Calibration loop

- Roadmap/check-in asks "How many hours did this actually take?"
- `POST /api/plan/actuals` + `V20__plan_actuals.sql`.
- Aggregate by `option_id + task_type`; when `sample_count >= N` (start **N=20**), update `task_fit.speedup` and set `confidence: "measured"`.
- Badge shows `measured (n=…)`. Never expose other users’ raw hours.
- Done when: at least one option can move placeholder → measured in a Java integration/unit test with synthetic aggregates.

### Step 9 — Share + gate

- `ShareVariantCard` (simple canvas or DOM capture of compare summary — not a marketing poster factory).
- `V21__plan_variant_limits.sql`: `max_plan_variants` free=1, pro=3, team=3.
- Creating variant B+ returns **402** with existing upgrade prompt pattern when over limit.
- Done when: free users keep one variant; Pro can compare three; share exports active comparison.

---

## 9. Chat paste blocks

### Creative foundation

```
Read CURSOR_PLAN_CREATIVE_UI.md, BRIEF.md, .cursor/rules/gyanwire.mdc.
MODEL FREEZE. Implement only step C0 (tokens + doc wiring). No What-If UI yet.
```

```
… Implement only step C1 (Trail map + Week sheet open).
```

```
… Implement only step C2 (Gear pack).
```

```
… Implement only step C3 (Fuel gauge + Week sheet polish).
```

### What-If

```
Read CURSOR_PLAN_CREATIVE_UI.md and .cursor/rules/gyanwire.mdc.
MODEL FREEZE. Implement only What-If Step 1 (V19 catalog extension, GET /api/options,
taskType on skill weeks, usePlanSimulator + Vitest). No swap UI.
```

(Repeat for Steps 2–9, naming only that step.)

---

## 10. Rules for every step

- Estimates are always ranges with a confidence badge; never a lone efficiency number.
- No hard-coded multipliers in components; they come from the catalog.
- Prices carry `pricedAt` (or `last_verified_at`); stale → "check current price".
- No affiliate or sponsored ordering.
- Components never call APIs; composables → services.
- Every new API endpoint: validation + basic Spring test.
- New DB: Flyway + RLS lockdown pattern from V5/V18 era.
- Do not change LLM model, provider, or completion settings.
- Update `ARCHITECTURE.md` when an API or table lands.

---

## 11. Success checks

- User can try three tool combinations in under a minute (Pro).
- Every efficiency figure has a visible range and an explanation.
- Simulator sometimes recommends staying free.
- After enough logged actuals, at least one option badge becomes `measured (n=…)`.
- Trail / Gear / Fuel / Week sheet feel like one composition with the existing Plan-to-finish brand, not a bolted dashboard.

---

## 12. Out of scope (park)

- Server-synced variants across devices (after Step 8 if needed).
- Embedding-based tool recommendations.
- Changing `weeks_saved` planner logic to use simulator speedups (planner stays rule-based; simulator is user-facing what-if only).
- Affiliate links or sponsored sort.
- Model / LLM changes (see `docs/PARKED_MODEL_WORK.md`).
