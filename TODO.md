# QuiStock Mobile roadmap

This roadmap follows the initial build, production-defaults, and CI/TDD preparation work. It is not an instruction to implement every item. Start an item only when its entry criterion is met, and deliver it in its own PR or with the feature that needs it. Check the current code and CI before treating an item as unfinished.

## General rule for new features

**Start when:** There is a real story, requirement, or bug to address.

**Do:** Follow Red–Green–Refactor; test observable success, failure, and relevant boundaries; update documentation when setup, architecture, or operation changes. Do not add coverage exclusions to make a gate pass. Use a spec for behavior changes as described in [docs/sdd/README.md](docs/sdd/README.md).

**Done when:** Behavioral tests detect a deliberately broken implementation; the relevant CI checks pass; the PR explains the behavior and evidence. CI runs `spotlessCheck`, `lintDebug`, `detekt`, debug/release builds, unit tests, and instrumented tests. Its `jacocoAggregateCoverageVerification` gate combines unit and instrumented coverage and requires at least 80% line and 70% branch coverage. Local `jacocoTestCoverageVerification` requires a connected device or emulator.

## 1. Keep feature architecture aligned with real needs

**Status:** In use. Login and chatbot already cross presentation, domain, and data boundaries; Koin is configured. This is an ongoing rule, not a request to introduce another architecture.

**Start when:** A feature needs network access, persistence, authentication, or a reusable business rule.

**Do:** Separate UI, state, use cases, and data access. Add interfaces at external boundaries or where multiple implementations are useful. Record significant decisions in the README or a short decision record.

**Done when:** Business rules can be tested without real Android UI, network, or database dependencies; the UI does not contain business rules or direct infrastructure access; the structure is justified by existing features.

## 2. Add instrumented tests for critical user flows

**Status:** In use. An instrumented login test exists and runs in CI. Assess coverage for each new complete flow.

**Start when:** A real end-to-end user flow can be exercised, such as adding a product, viewing stock, or recording a movement.

**Do:** Test navigation, interaction, and visible results for the main path. Use controlled data and avoid unstable external services. Keep tests repeatable and independent of run order.

**Done when:** The test fails if the main flow breaks and the team has a documented local command: `./gradlew connectedDebugAndroidTest` with a device or emulator.

## 3. Maintain branch coverage

**Status:** Coverage thresholds are configured: 80% of lines and 70% of branches in the combined JaCoCo gate.

**Start when:** A change introduces meaningful decisions, such as validation, permissions, stock states, or network-result handling.

**Do:** Test relevant success, failure, and boundary paths. Avoid tests that merely execute irrelevant or unreachable branches.

**Done when:** Critical decisions have understandable behavioral tests and any deliberate gaps are explained in the PR without broad package exclusions.

## 4. Review persistence and backup policy

**Status:** Local user preferences exist and Android backup is disabled. Review the data inventory before enabling backup or adding user-created data.

**Start when:** Persistence grows to include session, database, files, or other user-created data, or backup becomes a product requirement.

**Do:** Classify data as restorable, temporary, sensitive, or device-specific. Keep tokens, credentials, caches, and device identifiers out of backup. If restoration has value, define explicit inclusion and exclusion rules and test reinstall and device transfer before release.

**Done when:** Included and excluded data are documented; no secret or device-specific identifier is restored; restoration behavior is predictable and tested.

## 5. Separate Firebase environments

**Start when:** External users test the app; real customer, order, stock, or credential data is stored; development activity could affect a demo or retained data; experimental rules or indexes could affect production; or debug and release metrics must be separated.

**Do:** Create at least `dev` and `prod` environments. Add product flavors only then. Place `google-services.json` in the appropriate source sets, prevent development builds from writing to production, and document how authorized team members obtain configuration.

**Done when:** Development builds cannot accidentally write to production, environments can be identified in data and metrics, and both variants build in CI.

## 6. Harden backend access rules

**Status:** Firebase Auth and an internal backend client are present. Review rules for the specific backend resources a feature uses; client-side validation alone is insufficient.

**Start when:** A feature exposes protected data through Firebase or another network backend.

**Do:** Apply least privilege; test allowed and denied access; define ownership and roles only when required.

**Done when:** Unauthenticated or unauthorized users cannot access protected data, rules have automated tests or a reproducible validation procedure, and broad temporary public access is removed.

## 7. Prepare signing, AAB, and distribution

**Start when:** The team decides to distribute the app beyond its own devices, including to testers or reviewers.

**Do:** Produce an Android App Bundle, protect the upload key outside Git, make `versionCode` monotonic, relate `versionName` to tags, retain R8 mappings, and write a manual release checklist before automating publication.

**Done when:** An authorized team member can reproduce a signed build and relate the artifact, source, version, and mapping without changing the app identity.

## 8. Expand the Android test matrix

**Start when:** Behavior varies by Android version, uses version-sensitive APIs, or a device-specific failure appears.

**Do:** Test at least the supported `minSdk` and current `targetSdk`. Add intermediate versions only for relevant behavior. Keep critical flows in CI and run a wider matrix before releases or on a schedule if needed.

**Done when:** Affected flows pass at the supported extremes, differences are tested or documented, and CI remains practical.

## 9. Automate dependency updates and verification

**Start when:** Direct dependencies become numerous, manual updates are missed, or transitive changes cause hard-to-diagnose failures.

**Do:** Configure weekly Dependabot or Renovate PRs. Keep major AGP, Gradle, and Kotlin updates separate. Add dependency verification or locking if resolution varies between machines. Run the full pipeline before accepting updates.

**Done when:** Updates arrive as reviewable PRs, version choices are explainable, and local and CI builds resolve the expected dependencies.

## 10. Establish production observability

**Status:** Analytics, Crashlytics, and error reporters are configured. Confirm collection behavior and operational practices before external distribution.

**Start when:** The app is used outside the team and failures cannot reliably be reproduced locally.

**Do:** Confirm Crashlytics collection policy for distribution builds, keep personal data and secrets out of logs and events, define Analytics events around useful product questions, and review crashes, ANRs, and regressions before releases.

**Done when:** Release failures can be tied to a version and deobfuscated; event purpose and allowed data are documented; ownership and release-blocking rules are clear.

## 11. Review accessibility and internationalization

**Status:** User-facing screens exist, so apply these checks to current screens and each new UI change.

**Do:** Keep visible text in resources, format numbers/currencies/dates for the locale, check contrast, dark mode, touch targets and font scaling, and add content descriptions where native text or semantics are insufficient. Check a critical flow with a screen reader before delivery.

**Done when:** Android Lint has no new relevant findings, flows remain usable with larger fonts and both themes, and displayed values follow the selected locale.

## 12. Consider modularization

**Start when:** At least three relatively independent feature areas exist and coupling, build times, or repeated build-file conflicts cause measurable problems.

**Do:** Measure the problem first, extract one clear boundary at a time, and preserve directed dependencies and tests.

**Done when:** Extraction measurably reduces coupling, conflicts, or build time without disproportionate navigation and configuration complexity.

## 13. Consider Baseline Profiles and performance tests

**Start when:** Stable real flows exist and there is measurable startup delay, jank, slow interaction, or an upcoming wider distribution.

**Do:** Measure before optimizing, benchmark startup and critical interactions, generate profiles only for stable real journeys, and compare on a consistent device or test environment.

**Done when:** The improvement is measurable and the profile can be regenerated with a documented process.

## 14. Review quality policy periodically

**Start when:** A delivery milestone arrives or quality gates repeatedly fail without indicating actionable defects.

**Do:** Review coverage, exclusions, CI duration, flaky tests, and test usefulness. Fix fragile tests; record any reduction in rigor with a reason, owner, and review date.

**Done when:** Gates remain practical for each PR, failures are actionable, and exceptions are few and explicit.
