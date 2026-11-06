# Independent final security review — 2026-10-16

Scope: confirmed JWT + Redis migration spec/plan, execution ledger, migration production source and relevant tests. Existing dirty baseline was retained. This is the initial review before fixes; it does not certify the implementer's ongoing browser/cutover work.

Result: **two Important findings, no Critical findings identified**. Both concern identity races. Backend JWT/Redis implementation inspection did not identify a concrete authentication bypass within this review.

## Important 1 — A protected write can cross identities during its CSRF preflight

- Primary location: `D:/codex/English Hero/frontend/src/api/identity.ts:25` (lines 25–27); same pattern at line 45.
- Related location: `D:/codex/English Hero/frontend/src/auth/auth-transport.ts:47` (generation is first captured at line 50).
- Trigger: an account-A profile/password/binding action starts and waits for `writeOptions`/`csrfOptions`; account B becomes current in another tab and its identity notification is processed; the old CSRF request then resolves. `authenticatedJson` takes the **new** generation and token, so it sends the old action body under B's authorization. The store's generation check only ignores the response after the write; it cannot undo the server mutation. Component unmount also does not cancel this pending call.
- User effect: a nickname entered for A can modify B; other protected writes can likewise be attempted for the wrong account. Password/current-password checks narrow some consequences but do not protect profile changes.
- Evidence: read-only Node VM harness transpiled the actual `identity.ts`, `auth-transport.ts`, and `token-session.ts` into memory, mocked the raw HTTP boundary, paused A's CSRF request, advanced to B, and resumed the request. Output was `{"oldProfileWriteSentWithNewIdentity":true,"oldBodyPreserved":true}`; command exit 0. No real tokens, network calls, or files were used by the harness.
- Required correction: capture expected auth generation before the first asynchronous preflight, preserve it across token restoration and retries, and reject a stale operation before sending the protected write. Cover `write` and direct protected calls such as `accountEmailCode`. Add a delayed-CSRF + cross-account regression that asserts zero business writes.

## Important 2 — Password change is outside credential serialization and can erase a later login

- Primary location: `D:/codex/English Hero/frontend/src/api/identity.ts:57`.
- Related locations: `D:/codex/English Hero/backend/src/main/java/com/englishhero/identity/IdentityController.java:76`, `D:/codex/English Hero/frontend/src/views/AccountView.vue:91`.
- Trigger: A's password request passes authentication/CSRF and remains in flight. Another tab logs into B while that request runs: password change uses ordinary `write`, so it neither holds the credential Web Lock nor marks a credential mutation. A's eventual HTTP response unconditionally deletes the shared refresh and CSRF cookies, overwriting the cookies set by B's newer login. Its component callback also unconditionally clears the auth store even if B has already been restored; an unmounted async handler continues running.
- User effect: a successful new login loses its recovery cookie and can be cleared by a stale password completion. On reload or access expiry B is unexpectedly logged out. Password change also lacks the corresponding cross-tab invalidation broadcast.
- Evidence: a second in-memory harness using the actual modules deferred the password response, completed `api.login` for B, then completed the old password call. Output was `{"loginCompletedWhilePasswordPending":true,"stalePasswordCompletionAccepted":true}`; command exit 0. Cookie consequences are established by the controller's unconditional `cookies.clear` and frontend unconditional `auth.clear`; no actual password mutation or browser cookie operation was performed.
- Required correction: make password change part of the same credential-operation ordering used by refresh/login/logout, including generation invalidation, cross-tab signaling, and stale-completion handling. Account for refresh-before-password without recursively acquiring the same lock. A generation check only after the response cannot prevent its Set-Cookie header from erasing a newer login.

## Examined with no additional Important/Critical finding

- Fixed RS256/`at+jwt`/kid verification, controlled public key, strict issuer/audience and numeric/identity claims, exp clipping; token-bearing records redact `toString`.
- Redis atomic current-digest replacement, used-digest replay revocation, random-wrong-secret rejection without revocation, same-slot key naming, fixed absolute TTL, idle and absolute deadline comparisons.
- Bearer authentication checks current MySQL status/security version/roles plus Redis before reporting token expiry; Redis errors fail closed. Expired access does not extend activity.
- Logout revocation requires a matched refresh digest/used digest or verified current bearer; arbitrary sid is insufficient.
- Cookie path/host-only/HttpOnly/SameSite/Secure settings, remaining remembered Max-Age, stateless CSRF repository, Origin/Referer allowlist for login/refresh/logout, public recovery ignoring unrelated Bearer headers.
- Existing single-flight/one-retry transport behavior, per-tab and Web Lock coordination for login/refresh/logout, remote identity clearing and RouterView identity keys.
- Project-specific Redis launch/stop paths, process ownership guard, ignored generated config and key locations, production Secure/namespace startup checks, documented snapshot namespace change and Session rollback isolation.

## Evidence limits / declined to judge

- Did not rerun Maven, database, Redis, outage, browser, mail, or service-cutover tests. Reported suite counts in the ledger remain implementer evidence; this reviewer only ran the two isolated, in-memory reproductions.
- Did not inspect actual local configuration, private keys, credentials, live Redis values, or user records. Cannot certify their actual ACLs, entropy, secret distribution, runtime topology, or production configuration.
- Did not assess production Redis platform support, backup/restore procedures in operation, reverse-proxy/TLS deployment, key-rotation operations, or real email delivery.
- Did not re-review the entire pre-existing email/identity/UI baseline, nor claim visual acceptance.
- Existing tests inspected do not demonstrate all race scenarios in the plan: token browser tests exercise concurrent restoration and logout broadcast, but the account-switch-during-CSRF and password-versus-login cases above were absent.
- No assertion that requests already authenticated before a concurrent logout/password change must retroactively stop; normal in-flight authorization linearization is distinct from these cross-identity credential races.

Only this review artifact was written. No source, service, database, Redis, real configuration, or key files were modified by the reviewer.

## Implementer resolution — 2026-10-16

Both Important findings were reproduced with failing regression tests and fixed in one review fix pass. This section is implementer verification, not a second independent review.

- CSRF preflight: capture auth generation at the start of every protected write, before CSRF awaits, and enforce it through authenticated transport. `never sends an earlier account write after identity changes during CSRF lookup` failed before the fix, passed afterward; no cross-identity write is sent.
- Password changes: join the same local/Web Lock credential queue as login/refresh/logout. Refresh of expired access occurs inside the held lock without recursive locking. Invalidate stale generations, broadcast credential invalidation, and let the Pinia password action ignore late completion after another account takes over. Transport tests for password/login ordering and locked refresh and a store late-completion regression all failed first, then passed.
- Final affected suite: 38 frontend unit tests, typecheck/build, 104 desktop/mobile browser cases passed (100 Mock +4 real dedicated MySQL/Redis/local SMTP flows). Backend source unchanged by review fixes; the prior full Maven verification passed 40 unit/MVC +30 real MySQL/Redis integration cases.

Review limits stand: no claim of production Redis/TLS/reverse-proxy/key-rotation/QQ delivery certification, no re-review of unrelated pre-existing UI work, and no promise to retroactively cancel a request already authorized before revocation. Next protected request/refresh enforces revocation. Actual secret files remain outside review output; controlled runtime tests provide local evidence only.
