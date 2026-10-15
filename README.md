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
cookie privately in data and sends Bearer to the exact configured Core origin. It sends no
authentication cookies; the independent legacy chatbot client receives no Core credentials.
Auth sends only refresh cookies to the configured refresh route; login sends no stored cookies.
Both clients disable redirects and connection retries. Renewal happens on demand, without timers.

Auth reads auth_base_url from app/src/main/res/values/config.xml; Gradle does not override it.
CORE_BASE_URL remains a build environment variable with an invalid dummy default.
Gradle does not load .env automatically. Auth uses root-relative /login and /refresh routes;
gateway prefixes and cookie refresh scope must match the remodeled API before deployment.
Big numbers continue to use a simulated source and validate session access before a protected fetch.

Local revocation destroys a dedicated Keystore key before removing the encrypted refresh
record. Disk removal is attempted even if key destruction fails. If both fail, the app reports
a local error and blocks the current session; durable revocation cannot be promised when all
local mechanisms fail. Unreadable ciphertext never restores a session. Other secret keys are
unaffected. A valid `Retry-After` temporarily disables manual retry after 429; reaching the
limit only re-enables the action and does not trigger a request.

### Tests

- Tests for use cases, ViewModels, repositories, and mappers belong in `app/src/test`, using the
  same package as the class under test.
- Tests that depend on an `Activity`, `Fragment`, Android resources, or a device belong in
  `app/src/androidTest`.
- Prefer testing ViewModels with mocked use cases and repositories with mocked data sources.

## Useful commands

- Format code: `./gradlew spotlessApply`
- Automatically fix code smells: `./gradlew detekt --auto-correct`
- Run unit tests: `./gradlew testDebugUnitTest`
- Run instrumentation tests (requires a connected device): `./gradlew connectedDebugAndroidTest`
- Validate local combined coverage (requires a connected device): `./gradlew jacocoTestCoverageVerification`
- CI validates downloaded unit and instrumented coverage with `jacocoAggregateCoverageVerification`.
