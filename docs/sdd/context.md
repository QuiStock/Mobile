# Project context for AI-assisted development

Updated on 2026-09-28 from repository files. This document guides discovery; confirm details in the code before changing behavior.

## Known state

- `QuiStock` is an Android project with one Gradle module, `:app`, Kotlin, Java 17, Views/XML, ViewBinding, and the `com.quistock.quistock` package.
- The README describes Clean Architecture with MVVM and the dependency direction `presentation -> domain <- data`. The implemented structure also has `domain/port` and Koin dependency injection; follow the existing code where the guide is illustrative.
- The code includes login, personal registration, home, and chatbot areas. Login uses a use case, an authentication port, and a Firebase Auth implementation. The chatbot uses a use case and a Retrofit repository for an internal API. These files do not establish that the product flows are complete.
- The backend URL comes from `BACKEND_BASE_URL` in the build process. `.env.example` explains its format; Gradle does not load `.env` automatically. A dummy URL is used when the variable is absent. Do not record private endpoints in specs.
- There are unit tests for domain, ViewModel, dependency injection, and infrastructure, plus an instrumented login test. Their presence does not imply complete coverage of every flow.

## Sources of truth

| Topic | Source |
| --- | --- |
| Intended architecture and commands | `README.md` |
| Quality rules and criteria for future work | `TODO.md` |
| Implemented behavior | `app/src/main`, `app/src/test`, `app/src/androidTest` |
| Build and coverage configuration | `app/build.gradle.kts` |
| Executed CI gates | `.github/workflows/ci.yaml` |
| Scope of a change | Current request, Jira issue, and relevant spec |

## Limits and precautions

- `TODO.md` contains proposals with entry criteria; do not implement items merely because they are listed.
- Confirm backend contracts, copy, navigation, permissions, and business rules against the request or an explicit source. Do not infer them from class names or layouts.
- CI runs debug/release builds, Spotless, Lint, Detekt, unit tests, and instrumented tests; the combined JaCoCo gate requires 80% line coverage and 70% branch coverage.
- Changes involving Firebase, the backend, or persistence must state the environment, affected data, and expected failures in the relevant spec.
