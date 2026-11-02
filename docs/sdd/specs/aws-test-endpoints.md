# AWS test endpoints

**Status:** In progress  
**Issue:** none  
**Product decision owner:** requester

## Problem and goal

Test the entire application against a fresh AWS environment before merging infrastructure changes.

## Scope

Build-time public Auth and Core endpoint overrides. Existing defaults remain available.

## Expected behavior

Gradle properties `quistock.authBaseUrl` and `quistock.coreBaseUrl` override the resource endpoints.
Each URL must be absolute HTTPS with no credentials, query or fragment. A trailing slash is normalized.
Login, CSRF, refresh and logout are relative to the Auth base URL; cookie staging compares resolved paths. Relative Retrofit paths retain the AWS `/auth/` and `/api/` prefixes.
Invalid properties stop configuration before packaging.

## Acceptance criteria and evidence

| ID | Behavior | Evidence |
| --- | --- | --- |
| AC-01 | Without overrides, existing Render URLs remain | Generated resource inspection |
| AC-02 | With AWS-style endpoints, prefixes remain | Generated resource inspection and guided device login/chat |
| AC-03 | Invalid URL fails configuration | Gradle invalid-property check |

## Technical impact

Only Gradle and URL resources change; no API contracts or authentication behavior change.

## Decisions and open questions

Actual AWS hostname is generated during deployment; pass it locally, never put credentials in properties.

## Implementation outcome

Build properties implemented. Regression tests exercise actual Retrofit login/CSRF/refresh/logout paths under /auth/, and cookie staging denies root and Core paths. Local Gradle checks could not run because the daemon cannot establish a loopback connection; CI and device validation remain required.
