# Instructions for AI agents

These instructions apply to the entire repository. Read [docs/sdd/context.md](docs/sdd/context.md) and the relevant spec before editing code. The README is the architecture reference; the code and tests show what is implemented.

## Workflow

1. Understand the request and find its spec in `docs/sdd/specs/`. For a behavior change without a spec, create one from the template before implementation. Purely mechanical fixes may skip a spec; explain why in the PR.
2. Record observable behavior, acceptance criteria, and error cases. Mark genuine unknowns as `Open`; do not invent product decisions, API contracts, or sensitive data.
3. Link each acceptance criterion to an automated test or a justified manual check. Follow Red–Green–Refactor for new behavior.
4. Implement only the scope authorized by the request and spec. Update the spec in the same change if the agreed behavior evolves.
5. Run relevant checks and report results and limitations accurately. Never claim a test passed unless you ran it.

## Code and architecture

- Follow the dependencies described in the README: presentation uses domain; infrastructure belongs in `data`; domain does not import Android, Retrofit, or Firebase.
- Keep the `com.quistock.quistock` package and the existing feature organization. Do not add layers, interfaces, or modules without a concrete need.
- The UI uses Views, XML, ViewBinding, and ViewModels; existing dependency injection uses Koin. Inspect neighboring files for established patterns before editing.
- Do not put credentials, tokens, `google-services.json`, private URLs, or personal data in specs, prompts, logs, or commits.
- Do not change quality gates, coverage exclusions, or architecture merely to make a check pass.

## Verification

- Unit tests: `./gradlew testDebugUnitTest` (Windows: `.\gradlew.bat testDebugUnitTest`).
- Quality checks: `spotlessCheck`, `detekt`, `lintDebug`, and `assembleRelease`.
- Instrumented tests: `connectedDebugAndroidTest`, when a device or emulator is available.
- CI combines unit and instrumented coverage and requires 80% line coverage and 70% branch coverage. Do not confuse the local unit test report with this combined gate.

See [docs/sdd/README.md](docs/sdd/README.md) for the process and [docs/sdd/specs/TEMPLATE.md](docs/sdd/specs/TEMPLATE.md) for new specs.
