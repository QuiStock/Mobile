# Project context for AI-assisted development

Updated on 2026-10-16 from repository files. This document guides discovery; confirm details in the code before changing behavior.

## Known state

- `QuiStock` is an Android project with one Gradle module, `:app`, Kotlin, Java 17, Views/XML, ViewBinding, and the `com.quistock.quistock` package.
- The README describes Clean Architecture with MVVM and the dependency direction `presentation -> domain <- data`. The current structure uses `domain/port` and Koin dependency injection; follow the existing code where the guide is illustrative.
- The code includes login, personal registration, home, and chatbot areas. Login uses the DS_Auth contract through `RetrofitAuthRepository` and `SessionUseCase`, with isolated response-cookie capture, generation-checked commits and encrypted refresh persistence. For the CSRF correction, root routes on the same HTTPS Auth origin and compatible cookie attributes were confirmed by the requester; real deployment smoke verification remains pending. The chatbot uses a use case and a Retrofit repository for an internal API. These files do not establish that the product flows are complete.
- The backend URL comes from `BACKEND_BASE_URL` in the build process. `.env.example` explains its format; Gradle does not load `.env` automatically. A dummy URL is used when the variable is absent. Do not record private endpoints in specs.
- There are unit tests for domain, ViewModel, dependency injection, and infrastructure, plus an instrumented login test. Their presence does not imply complete coverage of every flow.

- The cancelled indicator branch is no longer part of the login stack. Home retains main's static layout; SessionUseCase has no indicator/cache dependency. Login retains the Logger/Logcat adapter used for safe diagnostics.
- Profile logout now confirms the action, fences the session, clears local credentials and legacy user identity independently of a single bounded DS_Auth logout attempt, then clears navigation to Login. Local cleanup failures block protected content and offer local-only retry. See `specs/profile-logout.md`; execution checks for this delivery were deferred at the requester's instruction.

- Auth refresh now obtains an isolated, memory-only CSRF pair from `/csrf` and sends the JSON token in `X-CSRF-TOKEN` with refresh/CSRF cookies to `/refresh`. Protocol failures preserve eligible refresh and present manual recovery while protected navigation is hidden. HTTP follow-ups are blocked. See `specs/auth-refresh-csrf.md`; tests were written but execution/validation is deferred by the requester. Logout now has its own isolated CSRF exchange with one shared ten-second deadline; see `specs/profile-logout-csrf.md`. Execution checks remain deferred.

- Perfil agora tem implementação de consulta Core em `GET profile`, estado somente em memória por sessão e barreira de entrada coordenada por `SessionViewModel`/`MainActivity`. Home só é liberada para Repositor (`FUNCIONARIO`) ou Gerente (`GERENTE`) com loja vinculada. Headers, Editar perfil e Contato consomem o mesmo resultado. Sem recarga ao voltar do background; foto independente não bloqueia entrada. Ver `specs/profile-user-store-data.md`. Testes foram escritos, mas execução e validações foram delegadas ao solicitante; compatibilidade do vínculo de loja de gerentes no backend real não foi verificada.

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
