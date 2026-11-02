# Cross-platform release APK builder

**Status:** Complete  
**Issue:** none  
**Product decision owner:** requester

## Problem and goal

The root Python helper should build the official Android release APK on Windows, macOS, or Linux without installing it on a device.

## Scope

**In scope:** choose the platform Gradle wrapper, run `:app:assembleRelease`, show Gradle progress, and report the build result and APK path.  
**Out of scope:** choosing endpoint overrides, installing Android SDK/platform tools, installing the APK, or changing signing configuration.

## Expected behavior

Running `python build_release.py` invokes `gradlew.bat` on Windows or `./gradlew` on macOS/Linux from the repository root. Gradle output is inherited by the terminal and uses verbose console output. The helper reports a missing wrapper, process launch failure, nonzero Gradle result, or missing output APK with a nonzero exit status. On success it prints the APK path.

## Acceptance criteria and evidence

| ID | Observable behavior | Planned check |
| --- | --- | --- |
| AC-01 | Correct wrapper is chosen per platform and build runs from repository root | Code inspection and mocked subprocess check |
| AC-02 | Build progress is shown while Gradle runs | Direct stdout/stderr inheritance and `--console=verbose` inspection |
| AC-03 | Failures return nonzero; success reports the APK | Control-flow inspection and syntax check |

## Technical impact

Root-level `build_release.py` replaces the prior installation helper. No external Python dependencies.

## Decisions and open questions

| Item | Status | Source or decision owner |
| --- | --- | --- |
| APK location | Known: `app/build/outputs/apk/release/app-release.apk` | Android application release output convention |

## Implementation outcome

The helper now builds only the release APK; it no longer prompts for endpoints or invokes ADB. Python syntax and `git diff --check` were verified. A Gradle build was not executed because the local Gradle runtime could not establish its loopback connection.
