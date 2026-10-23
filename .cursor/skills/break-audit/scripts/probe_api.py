#!/usr/bin/env python3
"""API break probes for break-audit. Writes findings JSON. Never prints passwords."""

from __future__ import annotations

import argparse
import base64
import json
import re
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from http.cookiejar import CookieJar
from typing import Any


def b64url_json(obj: dict) -> str:
    raw = json.dumps(obj, separators=(",", ":")).encode()
    return base64.urlsafe_b64encode(raw).decode().rstrip("=")


class Session:
    """One CookieJar + opener so auth cookies stick across calls."""

    def __init__(self) -> None:
        self.jar = CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar))
        self.last_set_cookie: list[str] = []

    def request(
        self,
        method: str,
        url: str,
        *,
        body: dict | None = None,
        headers: dict | None = None,
        raw_body: bytes | None = None,
        content_type: str | None = None,
        timeout: float = 20.0,
        use_cookies: bool = True,
    ) -> tuple[int, Any, dict[str, str]]:
        data = raw_body
        hdrs = {"Accept": "application/json", **(headers or {})}
        if body is not None:
            data = json.dumps(body).encode()
            hdrs.setdefault("Content-Type", "application/json")
        if content_type:
            hdrs["Content-Type"] = content_type
        req = urllib.request.Request(url, data=data, headers=hdrs, method=method)
        opener = self.opener if use_cookies else urllib.request.build_opener()
        try:
            with opener.open(req, timeout=timeout) as resp:
                raw = resp.read()
                text = raw.decode("utf-8", errors="replace")
                if hasattr(resp.headers, "get_all"):
                    sc = resp.headers.get_all("Set-Cookie") or []
                    if sc:
                        self.last_set_cookie = list(sc)
                try:
                    parsed: Any = json.loads(text) if text else None
                except json.JSONDecodeError:
                    parsed = text
                return resp.status, parsed, dict(resp.headers.items())
        except urllib.error.HTTPError as e:
            raw = e.read()
            text = raw.decode("utf-8", errors="replace")
            try:
                parsed = json.loads(text) if text else None
            except json.JSONDecodeError:
                parsed = text
            return e.code, parsed, dict(e.headers.items()) if e.headers else {}


def finding(
    findings: list[dict],
    *,
    severity: str,
    title: str,
    evidence: str,
    fix: str,
    check_id: str = "",
) -> None:
    findings.append(
        {
            "severity": severity,
            "title": title,
            "evidence": evidence[:800],
            "fix": fix,
            "checkId": check_id,
        }
    )


def msg_of(parsed: Any) -> str:
    if isinstance(parsed, dict):
        return str(parsed.get("message") or parsed.get("error") or parsed.get("errorCode") or parsed)[:400]
    return str(parsed)[:400]


def run_probes(api: str, email: str, password: str, *, destructive: bool) -> dict:
    findings: list[dict] = []
    meta: dict[str, Any] = {"api": api, "email": email, "destructive": destructive}
    anon = Session()
    authed = Session()

    st, body, _ = anon.request("GET", f"{api}/api/health")
    meta["healthStatus"] = st
    if st != 200:
        finding(findings, severity="High", title="API health not OK", evidence=f"{st} {msg_of(body)}", fix="Ensure API is up before auditing.", check_id="A1")

    st, body, _ = anon.request("POST", f"{api}/api/auth/login", body={"email": "nosuch_break_audit@example.com", "password": "wrong"})
    if st not in (401, 403, 429):
        finding(findings, severity="Medium", title="Unexpected bad-login status", evidence=f"{st} {msg_of(body)}", fix="Return 401 with generic message.", check_id="B2")

    st, me, _ = authed.request("POST", f"{api}/api/auth/login", body={"email": email, "password": password})
    if st == 429:
        finding(findings, severity="High", title="Login rate-limited during audit", evidence=msg_of(me), fix="Wait and retry; keep probe to one login.", check_id="B4")
        return {"findings": findings, "meta": meta}
    if st != 200 or not isinstance(me, dict):
        finding(findings, severity="Critical", title="Login failed with supplied credentials", evidence=f"{st} {msg_of(me)}", fix="Verify email/password and API URL.", check_id="B1")
        return {"findings": findings, "meta": meta}

    set_cookies = authed.last_set_cookie
    meta["setCookies"] = [re.sub(r"(access_token|refresh_token)=[^;]+", r"\1=<redacted>", c) for c in set_cookies]
    joined = "\n".join(set_cookies)
    if set_cookies and "HttpOnly" not in joined:
        finding(findings, severity="High", title="Auth cookies missing HttpOnly", evidence=str(meta["setCookies"])[:300], fix="Set HttpOnly on access/refresh cookies.", check_id="A4")
    if api.startswith("https://") and set_cookies and "Secure" not in joined:
        finding(findings, severity="High", title="Auth cookies missing Secure on HTTPS", evidence=str(meta["setCookies"])[:300], fix="Set Secure on cookies in production.", check_id="A4")

    st, body, _ = anon.request("GET", f"{api}/api/auth/me", use_cookies=False)
    if st == 200:
        finding(findings, severity="High", title="/api/auth/me reachable without cookies", evidence=msg_of(body), fix="Require authentication.", check_id="B10")

    st, body, _ = authed.request("GET", f"{api}/api/admin/llm/summary")
    if st == 200:
        finding(findings, severity="Critical", title="Admin LLM summary accessible to normal user", evidence=msg_of(body), fix="Enforce admin role.", check_id="C1")
    elif st not in (401, 403):
        finding(findings, severity="Medium", title="Unexpected admin endpoint status", evidence=f"{st} {msg_of(body)}", fix="Return 403 for non-admins.", check_id="C1")

    st, body, _ = authed.request("GET", f"{api}/api/action-plans/not-a-uuid")
    if st >= 500:
        finding(
            findings,
            severity="High",
            title="Invalid UUID returns 500 INTERNAL_ERROR",
            evidence=f"{st} {msg_of(body)}",
            fix="Validate path params; return 400/404 without stack/details.",
            check_id="C3",
        )

    st, body, _ = anon.request("GET", f"{api}/api/auth/login", use_cookies=False)
    if st >= 500:
        finding(findings, severity="Medium", title="Wrong HTTP method returns 500", evidence=f"{st} {msg_of(body)}", fix="Return 405 Method Not Allowed.", check_id="C4")

    st, body, _ = anon.request(
        "POST",
        f"{api}/api/auth/login",
        raw_body=b"email=a&password=b",
        content_type="application/x-www-form-urlencoded",
        use_cookies=False,
    )
    if st >= 500:
        finding(findings, severity="Medium", title="Wrong Content-Type returns 500", evidence=f"{st} {msg_of(body)}", fix="Return 415 Unsupported Media Type.", check_id="C5")

    stamp = str(int(time.time()))
    st, body, _ = anon.request(
        "POST",
        f"{api}/api/auth/register",
        body={
            "email": f"breakaudit_{stamp}@example.com",
            "password": "TestPass123!",
            "firstName": "Break",
            "lastName": "Audit",
            "role": "admin",
        },
        use_cookies=False,
    )
    if st in (200, 201) and isinstance(body, dict):
        role = ((body.get("data") or {}) if isinstance(body.get("data"), dict) else {}).get("role")
        if role == "admin":
            finding(findings, severity="Critical", title="Register accepts role=admin", evidence=msg_of(body), fix="Ignore client-supplied role.", check_id="B6")

    header = b64url_json({"alg": "none", "typ": "JWT"})
    payload = b64url_json(
        {
            "sub": "00000000-0000-0000-0000-000000000001",
            "tokenType": "access",
            "role": "admin",
            "tokenVersion": 1,
            "aud": "web",
            "iat": int(time.time()),
            "exp": int(time.time()) + 3600,
        }
    )
    none_tok = f"{header}.{payload}."
    st, body, _ = anon.request(
        "GET",
        f"{api}/api/auth/me",
        headers={"Cookie": f"access_token={none_tok}"},
        use_cookies=False,
    )
    if st == 200:
        finding(findings, severity="Critical", title="JWT alg=none accepted", evidence=msg_of(body), fix="Reject alg none; verify signature.", check_id="B7")

    st, body, _ = anon.request("POST", f"{api}/api/auth/login", body={"email": "' OR 1=1--", "password": "x"}, use_cookies=False)
    if st == 200:
        finding(findings, severity="Critical", title="Login SQLi-style payload succeeded", evidence=msg_of(body), fix="Parameterized auth only.", check_id="D1")

    news_id = None
    st_ai, body_ai, _ = anon.request("GET", f"{api}/api/news/AI", use_cookies=False)
    st_lower, body_lower, _ = anon.request("GET", f"{api}/api/news/ai", use_cookies=False)
    if st_lower == 400 and st_ai == 200:
        finding(
            findings,
            severity="Medium",
            title="News industry path is case-sensitive",
            evidence=f"/api/news/ai → {st_lower} {msg_of(body_lower)}",
            fix="Normalize industry path case.",
            check_id="G3",
        )
    if isinstance(body_ai, dict):
        results = (body_ai.get("data") or {}).get("results") if isinstance(body_ai.get("data"), dict) else None
        if isinstance(results, list) and results:
            news_id = results[0].get("id")

    if news_id:
        st, body, _ = anon.request("POST", f"{api}/api/news/feedback", body={"itemId": news_id, "vote": 1}, use_cookies=False)
        if st == 200:
            finding(
                findings,
                severity="High",
                title="News feedback accepted without authentication",
                evidence=f"{st} {msg_of(body)}",
                fix="Require auth or strict anon + rate limit.",
                check_id="E1",
            )
            ok = 0
            for _ in range(5):
                s2, _, _ = anon.request("POST", f"{api}/api/news/feedback", body={"itemId": news_id, "vote": 1}, use_cookies=False)
                if s2 == 200:
                    ok += 1
            if ok >= 5:
                finding(
                    findings,
                    severity="High",
                    title="News feedback has no effective rate limit",
                    evidence=f"{ok}/5 identical unauth votes accepted",
                    fix="One vote per identity per item; rate-limit bursts.",
                    check_id="E3",
                )

        st, body, _ = anon.request("POST", f"{api}/api/news/dismiss", body={"itemId": news_id}, use_cookies=False)
        if st == 200:
            finding(
                findings,
                severity="High",
                title="News dismiss accepted without authentication",
                evidence=f"{st} {msg_of(body)}",
                fix="Require sign-in for dismiss, or bind tightly to anon cookie.",
                check_id="E2",
            )

    st, body, _ = anon.request("POST", f"{api}/api/billing/webhook", body={}, use_cookies=False)
    text = msg_of(body)
    if "SECRET" in text.upper() or "RAZORPAY" in text.upper():
        finding(
            findings,
            severity="High",
            title="Billing webhook error leaks configuration details",
            evidence=f"{st} {text}",
            fix="Return generic 503; log details server-side.",
            check_id="F1",
        )

    req = urllib.request.Request(
        f"{api}/api/auth/login",
        method="OPTIONS",
        headers={
            "Origin": "http://evil.example",
            "Access-Control-Request-Method": "POST",
            "Access-Control-Request-Headers": "content-type",
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            acao = resp.headers.get("Access-Control-Allow-Origin")
            if acao in ("http://evil.example", "*"):
                finding(findings, severity="High", title="CORS allows untrusted origin", evidence=f"ACAO={acao}", fix="Allow only known UI origins.", check_id="A5")
    except urllib.error.HTTPError:
        pass
    except Exception:
        pass

    st, body, _ = authed.request("GET", f"{api}/api/me/referral")
    st2, me_body, _ = authed.request("GET", f"{api}/api/auth/me")
    if st == 200 and st2 == 200 and isinstance(body, dict) and isinstance(me_body, dict):
        code = (body.get("data") or {}).get("code")
        user_id = str((me_body.get("data") or {}).get("userId") or "")
        if code and user_id and user_id.startswith(str(code)):
            finding(
                findings,
                severity="High",
                title="Referral code is predictable userId prefix",
                evidence=f"code={code} matches userId prefix",
                fix="Issue opaque random referral codes.",
                check_id="E5",
            )
        st3, body3, _ = authed.request("POST", f"{api}/api/referrals/redeem", body={"code": code})
        if st3 == 200:
            finding(findings, severity="High", title="Self-referral redeem succeeded", evidence=msg_of(body3), fix="Block redeeming own code.", check_id="E6")

    if news_id:
        def one_ideas() -> tuple[int, str]:
            s, b, _ = authed.request("POST", f"{api}/api/news/{news_id}/ideas", body={}, timeout=60)
            text_b = b if isinstance(b, str) else json.dumps(b)
            if "LIMIT_REACHED" in text_b:
                return 402, text_b[:120]
            return s, text_b[:120]

        codes = []
        with ThreadPoolExecutor(max_workers=3) as ex:
            futs = [ex.submit(one_ideas) for _ in range(3)]
            for fut in as_completed(futs):
                codes.append(fut.result()[0])
        # If already at limit, all 402 is fine; if all 200 under concurrency past dailyIdeaLimit, flag
        if codes.count(200) >= 3:
            finding(
                findings,
                severity="Medium",
                title="Parallel idea requests all succeeded (check quota race)",
                evidence=f"statuses={codes}",
                fix="Enforce atomic daily idea counter before LLM work.",
                check_id="E4",
            )

    if destructive:
        st, body, _ = authed.request("DELETE", f"{api}/api/me/profile/data")
        if st == 200:
            finding(
                findings,
                severity="Critical",
                title="Profile data deleted with single call (no step-up)",
                evidence=f"{st} {msg_of(body)}",
                fix="Require re-auth + UI confirmation for destructive delete.",
                check_id="DESTRUCTIVE",
            )
    else:
        meta["skippedDestructive"] = ["DELETE /api/me/profile/data"]

    st, body, _ = authed.request("GET", f"{api}/api/me/usage")
    meta["usage"] = body.get("data") if isinstance(body, dict) else None

    sev_rank = {"Critical": 0, "High": 1, "Medium": 2, "Low": 3, "Info": 4}
    findings.sort(key=lambda f: sev_rank.get(f["severity"], 9))
    meta["findingCount"] = len(findings)
    meta["criticalCount"] = sum(1 for f in findings if f["severity"] == "Critical")
    meta["highCount"] = sum(1 for f in findings if f["severity"] == "High")
    return {"findings": findings, "meta": meta}


def main() -> int:
    parser = argparse.ArgumentParser(description="Break-audit API probes")
    parser.add_argument("--site", default="", help="UI URL (metadata only)")
    parser.add_argument("--api", required=True, help="API base, e.g. http://localhost:8080")
    parser.add_argument("--email", required=True)
    parser.add_argument("--password", required=True)
    parser.add_argument("--out", default="probe-findings.json")
    parser.add_argument("--destructive", action="store_true")
    args = parser.parse_args()

    api = args.api.rstrip("/")
    result = run_probes(api, args.email, args.password, destructive=args.destructive)
    result["meta"]["site"] = args.site
    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(result, f, indent=2)
    summary = {
        "out": args.out,
        "findingCount": result["meta"].get("findingCount"),
        "criticalCount": result["meta"].get("criticalCount"),
        "highCount": result["meta"].get("highCount"),
        "titles": [f["title"] for f in result["findings"]],
        "plan": (result["meta"].get("usage") or {}).get("plan", {}).get("name") if isinstance(result["meta"].get("usage"), dict) else None,
    }
    print(json.dumps(summary, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
