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

A login request passes through these existing boundaries:

```text
LoginFragment
    -> LoginViewModel
        -> LoginUseCase
            -> AuthRepository (domain)
                -> MockAuthRepository (data)
            -> SessionUseCase (domain)
                -> SecretStorage and session cache ports
```

QUIS-128 implements the client-side token flow with a synthetic authentication source while the
real API contract is pending. `SessionUseCase` stores tokens, restores persisted sessions, shares
refresh attempts, and invalidates expired sessions and their cache. The named Koin `CoreHttpClient`
uses the explicit `CORE_BASE_URL` origin and never attaches Core tokens to the legacy chatbot client.
Core redirects are disabled so an authenticated operation cannot forward a token to another origin.
Gradle reads this variable from the build environment, with an invalid dummy origin as the fallback;
it does not load `.env` automatically. Big numbers still use a simulated source without HTTP.

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
