# QuiStock Mobile

## Architecture guide

The project follows a variation of **Clean Architecture with MVVM**, adapted for Android Views
(`Activity`, `Fragment`, XML, and ViewBinding). The code is organized into packages inside the
`app` module. The tree below shows current package boundaries; add subpackages as needed.

Dependencies must flow in the following direction:

```text
presentation -> domain <- data
                         ^
                 APIs and Firebase
```

The `domain` layer is the core of the application. It must not depend on Android, Retrofit,
Firebase, DTOs, or UI classes.

### Package structure

All Kotlin code should be placed under the `com.quistock.quistock` package:

```text
com.quistock.quistock/
├── app/
│   ├── QuiStockApplication.kt
│   ├── di/
│   └── navigation/
├── domain/
│   ├── model/
│   ├── port/
│   └── usecase/
├── data/
│   ├── remote/
│   │   ├── internal/
│   │   └── firebase/
│   ├── preferences/
│   └── observability/
└── presentation/
    └── <feature>/
```

Replace `<feature>` with an actual feature name, such as `login` or `chatbot`. This tree is a
guide to existing boundaries, not a request to create every possible package in advance.

### Where to place each file

| File type | Package | Responsibility |
| --- | --- | --- |
| `Activity` and `Fragment` | `presentation/<feature>/` | Receive View events and render the state exposed by the ViewModel |
| `ViewModel` | `presentation/<feature>/` | Coordinate use cases and produce the screen state |
| `UiState`, `UiEvent`, and `UiModel` | `presentation/<feature>/` | Represent UI-specific state, events, and formatted data |
| `RecyclerView.Adapter` and `ViewHolder` | `presentation/<feature>/adapter/` | Render lists and forward View interactions |
| Reusable UI components | `presentation/common/` | Share behavior used exclusively by the UI |
| Domain model | `domain/model/` | Represent concepts used by application business rules |
| Port or repository interface | `domain/port/` | Define operations required by the domain without exposing where data comes from |
| Use case | `domain/usecase/` | Execute a business action or rule, preferably with one responsibility per class |
| Port or repository implementation | `data/` under the relevant integration | Convert external results into domain models |
| Retrofit interface | `data/remote/<api>/` | Declare endpoints for an external API |
| Request/response DTO | `data/remote/<api>/dto/` | Represent only the API transport contract |
| DTO mapper | `data/remote/<api>/mapper/` | Convert DTOs into domain models and vice versa |
| Firebase integration | `data/remote/firebase/<service>/` | Encapsulate the Firebase service in use |
| Local preferences | `data/preferences/` | Implement local preference storage |
| Dependency injection module | `app/di/` | Build external clients and bind interfaces to their implementations |
| Global navigation | `app/navigation/` | Define routes and coordinate navigation between features |
| Error reporting | `data/observability/` | Send technical errors to configured reporters |

Layouts, drawables, strings, and other Android resources remain in `app/src/main/res`. Use names
that identify their feature, such as `fragment_login.xml` and `fragment_home.xml`.

### Dependency rules

- `presentation` may depend on `domain`, but it must not access Retrofit, Firebase, DAOs, or
  DTOs directly.
- `domain` must contain Kotlin-only code and must not import `android.*`, `androidx.*`, Retrofit,
  or Firebase.
- `data` may depend on `domain` to implement its repositories and produce domain models.
- DTOs and entities must not reach the ViewModel or View. Convert them into domain models inside
  the `data` layer.
- `Activity` and `Fragment` classes must not contain business rules. They observe ViewModel state
  and forward user events.
- A ViewModel must not know a repository implementation. It depends on use cases.
- Keep transport DTOs and mappers inside the relevant integration. Add a separate data source
  when it helps isolate communication details.
- Firebase SDK classes must remain in the `data` layer or in technical initialization code under
  `app`. The domain must never expose types such as `FirebaseUser` or `DocumentSnapshot`.

### Example flow

A login request passes through these boundaries:

```text
LoginFragment
    -> LoginViewModel
        -> LoginUseCase
            -> SessionUseCase
                -> AuthRepository (domain)
                    -> RetrofitAuthRepository (data)
                        -> isolated CookieJar and encrypted refresh store
                -> session cache ports
```

Authentication follows DS_Auth: login sends email, password and `platform=mobile`; login and
refresh return `access_token` and `refresh_token` cookies with no response body. The private
cookie custody uses server expiry, keeps access only in memory, and persists only refresh
metadata in encrypted DataStore. Legacy refresh values without expiry/scope are deleted.
Domain models expose generation/revision and availability, never credential values.

`SessionUseCase` restores sessions, shares refresh attempts, and fences response commits,
retries, cache writes and rendering by generation. The Core client extracts a valid access
cookie privately in data and sends Bearer to the exact configured Core origin. Core uses an
empty CookieJar and strips Cookie and CSRF headers; Bearer calls do not require Core CSRF.
It sends no authentication cookies; the independent legacy chatbot client receives no Core credentials.
Auth obtains a fresh CSRF pair from `GET /csrf` before each renewal. The GET sends no
stored credentials; `POST /refresh` sends only the refresh cookie, the temporary
`AUTH-XSRF-TOKEN` cookie and the masked JSON token in `X-CSRF-TOKEN`. CSRF stays in
memory for that attempt and is never persisted. Login sends no stored cookies.
A protocol/CSRF failure preserves eligible refresh credentials, blocks protected content
and offers manual recovery; no automatic renewal retry follows a failure. Auth calls
also block HTTP follow-ups, including an immediate retry requested by a 503 response.
See [the CSRF specification](docs/sdd/specs/auth-refresh-csrf.md); execution checks for
this implementation are deferred at the requester's instruction.
Both clients disable redirects and connection retries. Renewal happens on demand, without timers.

Auth reads auth_base_url from app/src/main/res/values/config.xml; Gradle does not override it.
CORE_BASE_URL remains a build environment variable with an invalid dummy default.
Gradle does not load .env automatically. Auth uses root-relative /login and /refresh routes;
The cookie jar isolates /refresh and /logout attempts on the configured Auth origin.
CSRF is received only from /csrf on that origin. Cookie path metadata remains unchanged.

Local revocation destroys a dedicated Keystore key before removing the encrypted refresh
record. Disk removal is attempted even if key destruction fails. If both fail, the app reports
a local error and blocks the current session; durable revocation cannot be promised when all
local mechanisms fail. Unreadable ciphertext never restores a session. Other secret keys are
unaffected. A valid `Retry-After` temporarily disables manual retry after 429; reaching the
limit only re-enables the action and does not trigger a request.

Profile logout asks for confirmation, immediately blocks the session, and revokes local credentials
independently of one remote logout exchange. With eligible refresh, an isolated `GET /csrf` precedes
`POST /logout` with the Auth CSRF cookie and masked JSON token in `X-CSRF-TOKEN`.
Both calls share a ten-second total deadline; preparation failure prevents POST. It removes
the legacy refresh and stored user identifier, clears session conversation state, and returns to
Login with the protected back stack removed. Local cleanup failure keeps protected content hidden
and offers local-only retry; remote failure after local success shows a warning on Login. The
activity ViewModel retains the operation across screen recreation. Logout responses never install
cookies into a newer session, and HTTP follow-ups cannot repeat the remote attempt. See the
[profile logout specification](docs/sdd/specs/profile-logout.md) for decisions and pending execution checks.

The authenticated Core profile is loaded once after login or session restoration before Home is
released. `SessionViewModel` keeps the loading/retry gate active until `ProfileUseCase` publishes
a valid profile for the current session generation. Only `FUNCIONARIO` (Repositor) and `GERENTE`
with a store assignment can enter; missing assignments and forbidden accounts revoke the local
session. Temporary profile failures retain the session and offer manual retry. Returning from
background reuses the in-memory profile. Shared profile slots in headers and the edit/contact
forms observe the same state; no profile or photo cache is written to disk. Photo loading uses
an independent unauthenticated HTTPS client and does not block navigation. See
[the profile specification](docs/sdd/specs/profile-user-store-data.md). Execution and validation
of this change are deferred at the requester's instruction.

### Tests

- Tests for use cases, ViewModels, repositories, and mappers belong in `app/src/test`, using the
  same package as the class under test.
- Tests that depend on an `Activity`, `Fragment`, Android resources, or a device belong in
  `app/src/androidTest`.
- Prefer testing ViewModels with mocked use cases and repositories with mocked data sources.

## Useful commands

### Design preview

The `demo` build installs as **QuiStock Demo**, alongside the regular app. Build it with
`./gradlew assembleDemo` (Windows: `.\gradlew.bat assembleDemo`). The APK is generated at
`app/build/outputs/apk/demo/app-demo.apk`.

The preview reuses the 28 existing page layouts. It starts at Splash, continues through
Onboarding and Login, then opens Home without credentials. **Criar conta** opens the personal
registration example. The persistent top button switches between operational and manager
views and returns to Home. Use the app buttons and bottom bars to explore each flow.

The banner identifies all results as examples. Forms can advance empty; confirmation dialogs
only navigate. Undefined actions display a message. The preview has a separate application ID,
no internet permission, no Firebase initialization, and no real session or business persistence.
Its routing code lives entirely in `app/src/demo`; debug and release keep the real feature fragments.

Run the preview's instrumented journeys with
`./gradlew -PdemoTests connectedDemoAndroidTest` on an emulator/device. Without that property,
the regular `connectedDebugAndroidTest` suite remains selected. See
[the navigation specification](docs/sdd/specs/design-preview-navigation.md) for scope and evidence.

- Format code: `./gradlew spotlessApply`
- Automatically fix code smells: `./gradlew detekt --auto-correct`
- Run unit tests: `./gradlew testDebugUnitTest`
- Run instrumentation tests (requires a connected device): `./gradlew connectedDebugAndroidTest`
- Validate local combined coverage (requires a connected device): `./gradlew jacocoTestCoverageVerification`
- CI validates downloaded unit and instrumented coverage with `jacocoAggregateCoverageVerification`.
