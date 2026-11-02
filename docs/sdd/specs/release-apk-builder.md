# Cross-platform release APK builder

**Status:** Complete  
**Issue:** none  
**Product decision owner:** requester

## Problem and goal

The root Python helper should build the official Android release APK on Windows, macOS, or Linux without installing it on a device.

## Scope

**In scope:** prompt for Core and Auth base URLs, choose the platform Gradle wrapper, run `:app:assembleRelease`, show Gradle progress, and report the build result and APK path.
**Out of scope:** installing Android SDK/platform tools, installing the APK, or changing signing configuration.

## Expected behavior

Running `python build_release.py` first prompts for Core URL and then Auth URL, as the former install helper did. Blank input omits that Gradle property and uses its configured default; nonblank input is passed as `-Pquistock.coreBaseUrl` or `-Pquistock.authBaseUrl`. It then invokes `gradlew.bat` on Windows or `./gradlew` on macOS/Linux from the repository root, running only `:app:assembleRelease`. Gradle output is inherited by the terminal and uses verbose console output. The helper reports a missing wrapper, process launch failure, nonzero Gradle result, or missing output APK with a nonzero exit status. On success it prints the APK path.

## Acceptance criteria and evidence

| ID | Observable behavior | Planned check |
| --- | --- | --- |
| AC-01 | Core and Auth prompts appear in order and blank values are omitted | Code inspection and mocked input/subprocess check |
| AC-02 | Entered endpoints become Gradle properties and only the release APK is built | Code inspection and mocked subprocess check |
| AC-03 | Correct wrapper is chosen per platform and build runs from repository root with visible progress | Direct stdout/stderr inheritance and `--console=verbose` inspection |
| AC-04 | Failures return nonzero; success reports the APK | Control-flow inspection and syntax check |

## Technical impact

Root-level `build_release.py` replaces the prior installation helper. No external Python dependencies.

## Decisions and open questions

| Item | Status | Source or decision owner |
| --- | --- | --- |
| APK location | Known: `app/build/outputs/apk/release/app-release.apk` | Android application release output convention |

## Implementation outcome

The helper prompts for Core and Auth URLs, passes nonblank values as Gradle properties, builds only the release APK, and does not invoke ADB. Python syntax plus mocked Windows/macOS/Linux wrapper and URL forwarding paths passed; `git diff --check` passed. A Gradle build was not executed because the local Gradle runtime could not establish its loopback connection.
