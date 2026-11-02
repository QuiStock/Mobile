# Release APK signing

**Status:** Complete  
**Issue:** none  
**Product decision owner:** requester

## Problem and goal

The `release` APK is currently unsigned, so it cannot be installed as a normal release package and its signing certificate cannot be used as the stable Android app identity for Google Maps restrictions.

## Scope

**In scope:** configure the Android release build to use a shared release keystore, reading its path and credentials from environment variables or ignored `local.properties`.  
**Out of scope:** generating, storing, distributing, or committing the private keystore; Play App Signing configuration.

## Expected behavior

When all release signing values are provided, `assembleRelease` signs with that keystore and alias. Environment variables take precedence over local properties. Supplying only some values fails Gradle configuration with a clear message. When no values are supplied, non-release development work remains usable and release output remains unsigned; operators must configure the shared key before treating a release APK as official.

## Acceptance criteria and evidence

| ID | Observable behavior | Planned check |
| --- | --- | --- |
| AC-01 | Complete local properties configure release signing | Inspect resolved Gradle signing configuration / `signingReport` |
| AC-02 | Environment values take precedence over local properties | Gradle configuration check with synthetic values |
| AC-03 | Partial configuration fails with a clear message | Gradle configuration check with incomplete synthetic values |
| AC-04 | No signing values leave debug configuration usable | Relevant Gradle task configuration |

## Technical impact

`app/build.gradle.kts`, `.gitignore`, and setup documentation. The private key and passwords must remain outside version control and be shared through an approved secure channel.

## Decisions and open questions

| Item | Status | Source or decision owner |
| --- | --- | --- |
| Existing package ID for Maps restriction | Known: `com.quistock.quistock` | app `defaultConfig` |
| Play distribution signing key | Open | Product/release owner |

## Implementation outcome

`app/build.gradle.kts` now reads signing values from `QUISTOCK_RELEASE_*` environment variables,
falling back to the corresponding `release.*` values in ignored `local.properties`. Partial
configuration fails fast; absent values leave the release build unsigned so debug work remains
available. README documents local and CI setup. `git diff --check` passed. Gradle signing report
and signed APK verification remain to be run on a machine with a usable Gradle cache and the
shared keystore.
