# Store location in Profile

## Instrumentation correction — 2026-11-04, 15:13 report

Geocoding tests accessed Geocoder.GeocodeListener on Android 12, where that API
is unavailable. Fixtures now use the synchronous two-argument lookup below API
33 and keep listener stubs in a separate API-33-only object. First-result,
empty/error, thrown exception and missing-geocoder checks remain for each device's
supported path. MapsLauncher tests use recorded intents from a ContextWrapper;
map rendering tests use a MapView subclass instead of constructor interception.
This preserves AC-04/AC-06/AC-07 checks without reflecting over framework classes
or starting the Maps renderer. No production geocoder or maps behavior changed.

`git diff --check` passed; Gradle checks could not start due to a loopback connection
error. Device execution is still required to confirm these corrections.

**Status:** In progress  
**Issue:** none  
**Product decision owner:** open

## Problem and goal

Employees and managers need to see where their linked store is and open it in a maps app
without leaving the Profile screen.

## Scope

**In scope:**
- "Localização" section in `ProfileFragment`, shared by EMPLOYEE and MANAGER (same destination).
- Store address from Core `GET branches` (`address`, `city`, `state`, `latitude`, `longitude`),
  selected by the profile `storeId`.
- Button that opens the location in the installed maps app; browser fallback.
- Interactive map (full `MapView` since 2026-11-05, see [user-location.md](user-location.md); dark purple style).
  Centered on the stored coordinates, or on the address found by the device `Geocoder` when coordinates are
  null (map position only).

**Out of scope:**
- Device GPS, location permission, background tracking, route calculation inside the app.
  The user position and distance were added later in [user-location.md](user-location.md).
- Editing store address; geocoding addresses to coordinates.

## Expected behavior

- Loading: skeleton pulse in the card.
- Coordinates valid: preview with marker + address; button opens `geo:` with the exact pin.
- Coordinates null/invalid but address present: the device geocodes the address (5 s limit) to center the
  preview; if that fails, a placeholder with a pin is shown. The button opens a maps search for the address
  only (street, city, state; never the store name), so the pin is the maps app's best match.
- No address and no coordinates: "Endereço não cadastrado"; no button.
- Request/contract failure: message with "Tentar novamente".
- No maps app and no browser: error popup, no crash.

## Acceptance criteria and evidence

| ID | Observable given / when / then | Planned test or check |
| --- | --- | --- |
| AC-01 | Given a branches array, when the profile store id matches exactly one entry, then address, city, state are mapped. | `RetrofitStoreLocationRepositoryTests` |
| AC-02 | Given zero or several matching entries, or a non-2xx/IO/serialization error, then the result is Failure (cancellation propagates). | `RetrofitStoreLocationRepositoryTests` |
| AC-03 | Given only one of latitude/longitude, or out-of-range values, then coordinates are discarded and the address is used. | `RetrofitStoreLocationRepositoryTests` |
| AC-04 | Given coordinates, then the maps target is `lat,lng` with the store label; otherwise the address text without the store name. | `StoreLocationTests`, `MapsLauncherTests` |
| AC-09 | Given no coordinates and an address, then a geocoded point only centers the preview and never changes the maps query; a failed lookup keeps the placeholder. | `LoadStoreLocationUseCaseTests`, `AndroidAddressGeocoderTests` |
| AC-05 | Given no address and no coordinates, then the result is NoAddress. | `RetrofitStoreLocationRepositoryTests` |
| AC-06 | Given a Ready profile, when the screen loads, then Loading → Ready/NoAddress/Failure; retry reloads; same store is not reloaded on re-collect. | `StoreLocationViewModelTests`; rendering in `StoreLocationSectionTests` |
| AC-07 | Given EMPLOYEE or MANAGER, when Profile opens, then the section is shown. | Manual check (same layout and destination); instrumented test deferred |
| AC-08 | Given the section, then touch targets are at least 48dp and elements have content descriptions. | Manual TalkBack/visual check |

## Technical impact

Adds `StoreLocationApi`, `RetrofitStoreLocationRepository`, domain model/port/use case, ViewModel,
`view_store_location.xml`, drawables, strings, a `geo` `<queries>` entry, Koin registrations.
The Maps API key stays in `local.properties`; `secrets.defaults.properties` keeps a placeholder.

## Decisions and open questions

| Item | Status | Source or decision owner |
| --- | --- | --- |
| `branches` shape (address, city, state, latitude, longitude) | Confirmed by sample provided by the requester | Requester |
| Latitude/longitude are `null` in the sample, so the exact pin depends on backend data | Open | Backend |
| Whether Core authorizes `GET branches` for EMPLOYEE | Open (failure state covers a 403) | Backend |
| The Maps key was exposed in a working copy; rotate it and restrict it by package and SHA-1 | Open | Requester |
| Apple Maps | Not applicable: Android only | — |
| Map tiles need a Maps key authorized for the app (API enabled, package and SHA-1); an unauthorized key leaves the preview blank (`Authorization failure` in Logcat) | Open | Requester |

## Implementation outcome

Implemented. `spotlessCheck`, `testDebugUnitTest`, `detekt`, and `lintDebug` passed locally. The button was confirmed on a device. The preview map was blank on the device because the Maps key was rejected (`Authorization failure`); it has not been seen rendering. Instrumented tests were not run.

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
