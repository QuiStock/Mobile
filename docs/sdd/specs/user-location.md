# User location and distance to the store in Profile

## Instrumentation correction — 2026-11-04, 15:37 report

The next report completed with 122 tests and ten failures, without process death.
Nine failures are now confined to reflection on `LocationManager.isLocationEnabled`
in provider-test setup. Removing Context mocks alone was insufficient.

A narrow internal `DeviceLocationReader` now wraps the platform manager. The public
provider constructor still resolves the real service; provider selection, cached
fixes, the ten-second request timeout, cancellation and permission handling stay
the same. This boundary is needed because reflecting over the framework manager
also fails on the device. Tests inject a recording fake instead of mocking any
framework class; they still exercise the actual provider and assert cache choice,
fallback, disabled providers, permission denial/revocation and request counts.
A separate denied-permission test exercises the public platform constructor.

The remaining product test is documented in product-control-live-data.md.
Compilation, instrumented results and combined coverage after this revision remain
unconfirmed; no quality gate or coverage exclusion was changed.
`git diff --check` passed. The attempted unit tests, androidTest compilation,
Spotless, Detekt and lint stopped before task execution with
`Unable to establish loopback connection`.

## Instrumentation correction — 2026-11-04, 15:13 report

The Android 12 report has 103 tests and 26 failures. Nine location-provider tests
fail while MockK reflects over `Context.getSystemService`; subsequent context,
activity and map mocks repeat the reflection failure. Tests now use real
`ContextWrapper` implementations for service access, permissions, preferences and
recorded intents instead of mocking framework Context objects. Provider mocks are
released after each test, including setup failures.

UL-07 uses an internal constructor accepting the launch action to test the same
permission/first-launch decisions without mocking ComponentActivity. The public
constructor still registers RequestMultiplePermissions on the Activity before
start. UL-08 uses a MapView subclass with no renderer and an explicit lifecycle
owner; camera, marker and destruction assertions remain. No permission policy,
location delivery or API contract changed.

`git diff --check` passed. Gradle compilation, unit tests and Spotless could not
start because of `Unable to establish loopback connection`. Instrumented approval
is pending. The process termination during ProfileEditNavigationTests has no
testcase exception stack; the log shows minutes spent starting the Activity after
the reflection failures, so its exact cause is not established by this report.

**Status:** In progress  
**Issue:** none  
**Product decision owner:** open  
**Depends on:** [store-location.md](store-location.md) (unchanged by this spec)

## Problem and goal

Employees and managers want to see, in the Profile "Localização" card, where they are in relation to
their store and how far away it is.

## Scope

**In scope:**
- System location permission request the first time the app is opened (foreground only; precise or
  approximate, the user chooses on Android 12+).
- One current position of the device when Profile is shown (each time the screen starts).
- A "you are here" dot on the existing mini map next to the store pin, with the camera framing both.
- A distance line under the address: "Você está a 2,3 km da loja".

**Out of scope:**
- Background location, continuous tracking, routes or travel time inside the app.
- Sending the user's position to Core or any other service; storing it on the device.
- Any change to how the store location is loaded, shown, or opened in the maps app.

## Expected behavior

- First launch: the system dialog asks for location. It is shown once; denying it does not block the app.
- Profile, store with a point on the map (exact coordinates or geocoded address):
  - Permission granted: "Obtendo sua localização…", then the dot appears and the distance line shows it.
    If the store point came from the geocoded address, the text says "cerca de" (approximate).
  - Permission not granted: "Permitir localização para ver a distância até a loja". Tapping it shows the
    system dialog again while Android allows it; after a permanent denial it opens the app's system settings.
  - Location off, no fix, or 10 s timeout: "Não foi possível obter sua localização. Toque para tentar
    novamente." A position already shown stays on screen if a later read fails.
- Store without a point on the map (placeholder), no address, loading, or failure: no distance line;
  the store states are exactly as in `store-location.md`.
- Points less than 150 m apart: the map keeps the store zoom instead of zooming to street level.
- Distance format (pt-BR): under 1 km in meters rounded to 10 m ("350 m"); under 100 km with one
  decimal ("2,3 km"); then whole kilometers ("120 km").

## Acceptance criteria and evidence

| ID | Observable given / when / then | Planned test or check |
| --- | --- | --- |
| UL-01 | Given no location permission, when Profile refreshes the user location, then the state is PermissionNeeded and the device is not read. | `UserLocationViewModelTests` |
| UL-02 | Given permission, when the position is read, then Locating → Located; with no fix, Unavailable; a retry reads again. | `UserLocationViewModelTests`, `AndroidDeviceLocationProviderTests` |
| UL-03 | Given a known position, when a new read is pending or fails, then the known position stays; a refresh during a read does not start another read. | `UserLocationViewModelTests` |
| UL-04 | Given permission revoked between reads, when Profile refreshes, then the read is cancelled and PermissionNeeded is shown. | `UserLocationViewModelTests` |
| UL-05 | Given two points, then the distance is the haversine great-circle distance (symmetric, zero for the same point). | `GeoDistanceTests` |
| UL-06 | Given a distance in meters, then it is formatted as described above. | `DistanceFormatterTests` |
| UL-07 | Given a fresh install, when the app opens for the first time, then the system location dialog is shown once and not on later launches or recreation. | `LocationPermissionTests` (asks once, skips when already granted); the system dialog itself is a manual check on device (instrumented tests pre-grant the permission through `QuiStockTestRunner`) |
| UL-08 | Given a granted permission and a store point, when Profile opens, then the map shows the store pin and the user dot framed together, and the distance line is shown. | `StoreLocationSectionTests` (distance line, preview drawn with the user point); the visual framing is a manual check on device |
| UL-09 | Given a permanent denial, when the permission line is tapped, then the app's system settings open; returning to Profile re-reads the permission. | `LocationPermissionTests` (settings intent for the app package); the re-read on return is a manual check on device |
| UL-10 | The permission/retry line has a 48dp touch target and is read by TalkBack. | Manual TalkBack/visual check |

## Technical impact

- Manifest: `ACCESS_COARSE_LOCATION` and `ACCESS_FINE_LOCATION` (no background permission).
- Domain: `DeviceLocationProvider` and `LocationPromptPreferences` ports, `GeoPoint.distanceMetersTo`.
- Data: `AndroidDeviceLocationProvider` (platform `LocationManager` through `LocationManagerCompat`;
  no new dependency) and `AndroidLocationPromptPreferences` (one boolean in its own SharedPreferences file).
- Presentation: `FirstLaunchLocationPrompt` (MainActivity), `UserLocationViewModel`, `DistanceFormatter`,
  `StoreMapPreview` (map drawing extracted from `StoreLocationSection`, same style, pin, and click
  behavior), distance row in `view_store_location.xml`, `user_location_strings.xml`.
- Instrumented tests: `QuiStockTestRunner` grants location before tests so the first-launch dialog
  never takes window focus from Espresso.

## Privacy

The position is read only while Profile is visible, kept in memory in the ViewModel, used only to draw the
dot and compute the distance on the device, and never logged, persisted, or sent to a server.

## Decisions and open questions

| Item | Status | Source or decision owner |
| --- | --- | --- |
| Ask on first app launch (before login), not only when Profile opens | Decided | Requester |
| No in-app explanation screen before the system dialog | Open (can be added if product wants one) | Product |
| Distance is a straight line, not a route distance | Decided (routes stay in the maps app) | Requester scope |
| Store coordinates are null in the backend sample, so the distance uses the geocoded address and is approximate | Open | Backend |

## Implementation outcome

Implemented. Locally passed: `spotlessCheck`, `testDebugUnitTest` (399 tests, 0 failures), `detekt`,
`lintDebug`, `assembleRelease`, and `compileDebugAndroidTestKotlin`. Instrumented tests were not run (no
device or emulator connected). UL-07 to UL-10 were not checked on a device yet. The map preview still
depends on the Maps key authorization noted in `store-location.md`.

Coverage follow-up: CI failed `jacocoAggregateCoverageVerification` (branches 0.69 < 0.70) because the new
Android-bound location code had no tests. Instrumented tests were added (`StoreLocationSectionTests`,
`MapsLauncherTests`, `LocationPermissionTests`, `AndroidDeviceLocationProviderTests`,
`AndroidAddressGeocoderTests`). Locally on an API 36 emulator: `connectedDebugAndroidTest` (107 tests, 0
failures); combined unit + instrumented coverage 71.1% branches and 84.8% lines; gates unchanged.
To prevent headless CI emulator (`swiftshader_indirect`) `ColorBuffer` exhaustion and `adb: device offline`
timeouts on API 37, `StoreLocationSection` only attaches the `MapView` lifecycle when a store point is
shown, `ProfileTestDependencies` stubs store/device location in general UI fixtures, and
`StoreLocationSectionTests` inflates the section with a themed context and mocks the Maps delegate instead
of launching `MainActivity` or starting the live OpenGL map renderer.
