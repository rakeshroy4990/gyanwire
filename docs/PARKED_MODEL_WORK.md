# Parked model work

Do not start these until S16 in `CURSOR_PLAN_FLOW_FIRST.md` is done and `eval/reports/` has a baseline. If a step seems to need a model change, add a row here and stop.

| # | Item | Start condition | Notes |
|---|---|---|---|
| P1 | Choose new model(s). Run `evalSearch` plus idea and brief evals side by side. Decide on quality per rupee. | Flow complete, baseline stored | |
| P2 | Add `ModelRouter` (task → model) and per-plan model limits. | P1 decision | |
| P3 | Embeddings + pgvector + reranker. Compare against the S5 full-text hybrid. | P1, eval ready | |
| P4 | Provider-native structured outputs or tool use, where that beats JSON-in-prompt. | P2 | Current client uses `response_format: json_object` and temperature `0.2`. Leave both until this item. |
| P5 | Prompt caching, cache diagnostics, conversation compaction, effort per plan. | P2 | |
| P6 | Agent features: Watchers as agents, Research Tasks inbox, deep mode. | P2 and P5 | S14 digests stay a scheduled job, not an agent. |

Migration rule: change one thing at a time, run evals, and keep the previous `LLM_*` values for rollback.
