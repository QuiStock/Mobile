# Core request diagnostics

**Status:** In progress — implementation authorized; execution checks deferred  
**Issue:** none  
**Product decision owner:** requester

## Problem and goal

The requester needs to follow failures while loading the authenticated profile after the CSRF/logout rebase. Auth already emits structured debug/info events through Logger and LogcatLogger (tag QuiStock); Core and profile failures currently lack equivalent evidence. Source: direct request on 2026-10-20.

## Scope

**In scope:** structured diagnostics for Core calls, each HTTP attempt, session renewal/blocking, profile decoding and acceptance/rejection. Reuse Logger, transport failure categories, existing Core authentication and error handling.

**Out of scope:** logging bodies, headers, credentials, identifiers, names, emails, photo URLs, hosts, full URLs, query values, throwable messages or stack traces; changing backend/auth contracts, refresh policy, navigation or quality gates. No new remote telemetry or HTTP logging interceptor. The existing Auth logger's debug/info policy remains unchanged.

## Expected behavior

- Core request start: fixed message and operation (`core_profile` for the known configured profile route, otherwise `core_request`), method and safe HTTPS/placeholder/base-path flags. Only the configured Core origin is instrumented.
- Per-attempt start/response: debug events with attempt number, HTTP status and elapsed_ms. Initial attempt is 1; the existing single replay after 401 is 2. Log the response status before rejecting an obsolete response.
- Renewal: debug events with a static reason (`access_unavailable` or `unauthorized`) and safe access availability / generation-change flags. Auth's existing logs explain its own CSRF/refresh exchange.
- Transport failures: info events with the same allowlisted category function as Auth and elapsed_ms; propagate the original failure without logging the throwable. SessionException is reported as a blocked request with a safe stage and failure category, rather than a DNS/network error.
- Profile parsing: info event for empty or invalid responses, session unavailability or unexpected failures, without error text. Domain profile acceptance/rejection emits fixed events and only a static reason (`invalid`, `no_store`, `role`, `forbidden`). Cache reuse and cancellation may be debug events. No role value, account/store fields or body is included.
- No retries, response consumption, credential lifetime or result mapping change due to diagnostics. Logging never uses a response/profile/request toString.

## Acceptance criteria and evidence

| ID | Observable given / when / then | Planned evidence |
| --- | --- | --- |
| AC-01 | Given a Core request succeeds, its start and response events identify operation, attempt, status and duration without URL/headers/body. | CoreTransportDiagnosticsTests using a controlled chain and captured Logger events |
| AC-02 | Given 401 then successful renewal, diagnostics distinguish renewal and attempt 2 while preserving the single-replay policy. | CoreTransportDiagnosticsTests and existing CoreSessionInterceptorTests (not executed here) |
| AC-03 | Given a transport failure or unavailable access, diagnostics distinguish safe transport category from session blocking and preserve exceptions. | CoreTransportDiagnosticsTests; allowlisted keys and original exception checks |
| AC-04 | Given invalid JSON/empty response or rejected profile, diagnostics explain the category without personal fields or throwable data. | ProfileDiagnosticsTests and existing profile tests (not executed here) |
| AC-05 | Given logs are read on device, tag QuiStock and operation=core_profile make the request/renewal/profile sequence discoverable. | Requester manual Logcat check; real runtime output requires a device/run |

## Technical impact

Logger is injected into the named Core client, profile repository and ProfileUseCase. A per-request data-layer diagnostics helper reuses Auth's transportFailureCategory. Domain uses only its existing Logger port. No new storage or API calls. Nullable default logger parameters preserve isolated test constructors; application DI supplies the shared Logger explicitly. Backend environment and affected data are unchanged.

## Decisions and open questions

| Item | Status | Source |
| --- | --- | --- |
| Add logs using the Auth request pattern | Approved | Direct requester instruction |
| Do not run tests or final validations | Confirmed | Requester's standing instruction |
| Sensitive values and private endpoints stay out of diagnostics | Required | AGENTS.md and existing Auth diagnostic policy |

No open product or API decisions are needed for this diagnostic-only change.

## Implementation outcome

Implemented diagnostic events through the existing Logger/LogcatLogger. `CoreTransportDiagnostics` owns safe per-request/per-attempt metadata and reuses Auth transport categories; `CoreSessionInterceptor` preserves the existing request, generation, refresh and replay rules while reporting the stages. The profile repository classifies response/decoding errors; ProfileUseCase reports acceptance, rejection, cache reuse, sharing and cancellation without field values. Koin explicitly injects Logger into all three entry points. Constructors keep nullable defaults for existing isolated fixtures.

Written evidence: `CoreTransportDiagnosticsTests` (success, 401/replay, DNS failure, missing access and protocol block) and `ProfileDiagnosticsTests` (invalid/empty body, cancellation, missing store and cache reuse). Captured contexts are checked against an allowlist and synthetic sensitive markers; exception propagation, request counts and domain result types remain asserted. Tests were written but not executed, so Red–Green and passing checks are not established.

No build, test, Detekt, formatter/check, live API call, simulation or device Logcat verification was executed, per the requester's standing instruction. Diagnostic output and quality gates require the requester's subsequent execution. No contract, storage or quality-gate configuration was changed.
