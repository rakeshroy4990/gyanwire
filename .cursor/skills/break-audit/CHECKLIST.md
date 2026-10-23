# Break-audit checklist

Use with [SKILL.md](SKILL.md). Mark each item Pass / Fail / Skip (with reason).

## A. Transport & headers

| ID | Check | How |
|---|---|---|
| A1 | Health reachable | `GET {api}/api/health` |
| A2 | Security headers on API | `X-Frame-Options`, `X-Content-Type-Options`, no stack traces |
| A3 | Security headers on SPA | CSP, framing — often missing on Vite |
| A4 | Cookie flags | HttpOnly present; `Secure` on HTTPS; `SameSite` |
| A5 | CORS | OPTIONS from `http://evil.example` should not allow credentials |

## B. Authentication

| ID | Check | How |
|---|---|---|
| B1 | Login success | Valid email/password |
| B2 | Login failure message | Generic, no user enumeration in body |
| B3 | Login timing | Rough timing gap exist vs miss |
| B4 | Rate limit | Burst wrong passwords → 429 |
| B5 | Register validation | Weak email/password rejected |
| B6 | Role escalation on register | `role: admin` ignored |
| B7 | JWT alg none | Forged token rejected |
| B8 | Weak JWT secrets | Common secrets rejected |
| B9 | Session refresh | Refresh cookie renews access |
| B10 | Logout / me unauth | `/api/auth/me` 401 without cookie |

## C. Authorization & IDOR

| ID | Check | How |
|---|---|---|
| C1 | Admin without role | `/api/admin/*` → 403 |
| C2 | Billing checkout unauth | 401/403 |
| C3 | Bad UUID → not 500 | 400/404, not INTERNAL_ERROR 500 |
| C4 | Wrong HTTP method | 405, not 500 |
| C5 | Wrong Content-Type | 415, not 500 |
| C6 | Cross-user plan/idea id | 404/403 for random UUID |

## D. Injection & XSS

| ID | Check | How |
|---|---|---|
| D1 | Login SQLi-style | Rejected as bad credentials |
| D2 | Query title XSS | Reflects as text only (no HTML exec) |
| D3 | Query `javascript:` URL | Must not become clickable `href` |
| D4 | Profile XSS fields | Rejected or escaped |
| D5 | Research/chat XSS | No script execution |

## E. Abuse & quotas

| ID | Check | How |
|---|---|---|
| E1 | Feedback without auth | Should require auth or strict anon + rate limit |
| E2 | Dismiss without auth | Same |
| E3 | Feedback spam | N identical votes blocked after 1–few |
| E4 | Parallel quota race | Concurrent idea/search jobs must not all succeed past limit |
| E5 | Referral predictability | Code must not be userId prefix |
| E6 | Self-referral | Rejected |
| E7 | Quota UI drift | UI remaining matches `/api/me/usage` |

## F. Config leakage

| ID | Check | How |
|---|---|---|
| F1 | Billing webhook | No secret name in error body |
| F2 | Checkout unconfigured | Generic message |
| F3 | Actuator / env | Not publicly exposing secrets |

## G. Functional breaks

| ID | Check | How |
|---|---|---|
| G1 | First load empty flash | Skeleton until news arrives |
| G2 | Industry deep link | `?industry=` selects that industry |
| G3 | API path case | `/api/news/ai` vs `/AI` consistent |
| G4 | Pagination | Next/prev work |
| G5 | Limit-reached CTA | Upgrade / pricing path, not blank page |
| G6 | Core flows | Get an idea, research ask, profile save |

## H. UX / a11y / mobile

| ID | Check | How |
|---|---|---|
| H1 | Signal/idea copy quality | Not identical templates across cards |
| H2 | Unlabeled controls | Rating buttons have names |
| H3 | Contrast | Secondary chips readable |
| H4 | Mobile ~390px | No horizontal trap; CTAs reachable |
| H5 | Error tone | Actionable next step |

## Historical Gyanwire findings (re-test every run)

These failed in an Oct 2026 localhost audit. Treat as regression targets:

1. `javascript:` on Ideas “Open original article” via `?url=`
2. `DELETE /api/me/profile/data` with no confirmation (destructive — safe mode skips executing wipe)
3. Unauthenticated news feedback accepted repeatedly
4. Unauthenticated news dismiss by `itemId`
5. Parallel `/api/news/{id}/ideas` bypassed daily idea limit
6. Referral code = first 8 of user UUID
7. Invalid UUID / wrong method / wrong Content-Type → HTTP 500 INTERNAL_ERROR
8. Billing webhook error named missing `RAZORPAY_WEBHOOK_SECRET`
9. Ideas page limit-reached with no upgrade CTA
10. News signal blurbs duplicated across stories
11. Ideas often social-headline rewrites, not product ideas
12. First paint “Nothing here yet” with industry already selected
13. Guest/signed-in search quota UI flicker
14. `?industry=` deep link ignored
15. `/api/news/ai` case sensitivity UNKNOWN_INDUSTRY
16. Idea rating controls labeled only `f1`–`f5`
17. Auth cookies missing `Secure` on HTTPS (`AUTH_COOKIE_SECURE`)
18. SPA missing CSP / `X-Frame-Options`
19. Login inputs not using `v-model` (autofill / automation)
