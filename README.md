# Mobile

## Architecture guide

The project follows a variation of **Clean Architecture with MVVM**, adapted for Android Views
(`Activity`, `Fragment`, XML, and ViewBinding). The code is initially organized into packages
inside the `app` module.

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
│   ├── QuistockApplication.kt
│   ├── di/
│   └── navigation/
├── domain/
│   ├── model/
│   ├── repository/
│   └── usecase/
├── data/
│   ├── repository/
│   ├── remote/
│   │   ├── <api>/
│   │   └── firebase/
│   └── local/
├── presentation/
│   ├── common/
│   └── <feature>/
└── core/
    ├── error/
    ├── dispatcher/
    └── result/
```

Replace `<api>` and `<feature>` with the actual integration and feature names. For
example: `remote/brapi`, `remote/alphavantage`, `presentation/market`, and
`presentation/portfolio`.

### Where to place each file

| File type | Package | Responsibility |
| --- | --- | --- |
| `Activity` and `Fragment` | `presentation/<feature>/` | Receive View events and render the state exposed by the ViewModel |
| `ViewModel` | `presentation/<feature>/` | Coordinate use cases and produce the screen state |
| `UiState`, `UiEvent`, and `UiModel` | `presentation/<feature>/` | Represent UI-specific state, events, and formatted data |
| `RecyclerView.Adapter` and `ViewHolder` | `presentation/<feature>/adapter/` | Render lists and forward View interactions |
| Reusable UI components | `presentation/common/` | Share behavior used exclusively by the UI |
| Domain model | `domain/model/` | Represent concepts used by application business rules |
| Repository interface | `domain/repository/` | Define operations required by the domain without exposing where data comes from |
| Use case | `domain/usecase/<feature>/` | Execute a business action or rule, preferably with one responsibility per class |
| Repository implementation | `data/repository/` | Combine data sources and convert their results into domain models |
| Retrofit interface | `data/remote/<api>/` | Declare endpoints for an external API |
| Request/response DTO | `data/remote/<api>/dto/` | Represent only the API transport contract |
| DTO mapper | `data/remote/<api>/mapper/` | Convert DTOs into domain models and vice versa |
| Remote Data Source | `data/remote/<api>/` | Encapsulate API access and communication details |
| Firebase integration | `data/remote/firebase/<service>/` | Encapsulate Auth, Firestore, Storage, or Remote Config |
| Entity, DAO, and database | `data/local/` | Implement local persistence and caching |
| Dependency injection module | `app/di/` | Build external clients and bind interfaces to their implementations |
| Global navigation | `app/navigation/` | Define routes and coordinate navigation between features |
| Shared errors and results | `core/error/` and `core/result/` | Represent technical results shared by more than one layer or feature |

Layouts, drawables, strings, and other Android resources remain in `app/src/main/res`. Use names
that identify their feature, such as `fragment_market.xml`, `item_asset.xml`, and
`market_empty_state`.

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
- Each external API must have its own services, DTOs, mappers, and data sources.
- Firebase SDK classes must remain in the `data` layer or in technical initialization code under
  `app`. The domain must never expose types such as `FirebaseUser` or `DocumentSnapshot`.

### Example flow

A quote lookup should pass through the following components in order:

```text
MarketFragment
    -> MarketViewModel
        -> GetQuoteUseCase
            -> MarketRepository (interface in domain)
                -> MarketRepositoryImpl (data)
                    -> MarketRemoteDataSource
                        -> MarketApi (Retrofit)
```

On the return path, the repository converts `QuoteDto` into `Quote`. When necessary, the
presentation layer converts `Quote` into `QuoteUiModel`.

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
- Validate test coverage (requires a connected device): `./gradlew jacocoTestCoverageVerification`
