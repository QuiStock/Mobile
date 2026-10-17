# Maps screen

**Status:** In progress  
**Issue:** none  
**Product decision owner:** open

## Problem and goal

The application requires map visualization capabilities (`MapsFragment` / `fragment_maps.xml`) using Google Play Services Maps SDK and the Secrets Gradle Plugin to display geographic location points and routes.

## Scope

**In scope:**
- Integration of Google Maps Platform SDK and Secrets Gradle Plugin.
- Fragment container layout (`fragment_maps.xml`) hosting `SupportMapFragment`.
- `MapsFragment.kt` implementing `OnMapReadyCallback` formatted according to Spotless rules.
- Custom dark purple map theme styling JSON (`map_style_dark_purple.json`) adhering to the app brand palette.

**Out of scope:**
- Dynamic route calculation and GPS background tracking services (to be defined in subsequent feature tasks).

## Expected behavior

When `MapsFragment` is loaded, it initializes the `SupportMapFragment` asynchronously, registers the map callback, and positions the camera over the designated coordinates with an indicator marker.

## Acceptance criteria and evidence

| ID | Observable given / when / then | Planned test or check |
| --- | --- | --- |
| AC-01 | Given Google Play Services Maps SDK is integrated, when building the project, then the build succeeds and spotless checks pass. | Gradle verification (`spotlessCheck`, `testDebugUnitTest`). |
| AC-02 | Given `MapsFragment` is instantiated, when view is created, then the layout inflates `fragment_maps` and binds `SupportMapFragment`. | Component check. |

## Technical impact

Adds `play-services-maps` dependency and `secrets-gradle-plugin` to build configuration, adds `MapsFragment.kt` and `fragment_maps.xml`.

## Decisions and open questions

| Item | Status | Source or decision owner |
| --- | --- | --- |
| Final API key distribution and location points | Open | Product / Backend |

## Implementation outcome

Added `MapsFragment`, layout, dependencies, and formatting compliant with Spotless.
