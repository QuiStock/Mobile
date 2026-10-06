# Post-login skeleton loading screens

**Status:** In progress  
**Issue:** none  
**Product decision owner:** open

## Problem and goal

The authenticated routes currently render their content immediately, without a visual loading state. Add skeleton placeholders to communicate that each destination is loading.

## Scope

**In scope:** home, chatbot, order, order sent, promotion, promotion sent, interferences, product control, and product detail destinations in `nav_graph.xml`.  
**Out of scope:** login and registration, backend/data loading, changing navigation or page content.

## Expected behavior

When an authenticated destination is opened, a screen-shaped skeleton covers the destination content and prevents interaction while visible. The current screen content is then revealed after a short local loading transition. The skeleton uses the app's dark purple palette and animates subtly. It does not expose placeholder text to accessibility services.

The current destination screens do not expose data-loading state; this is a presentation transition for the existing static layouts. Connect dismissal to real load completion when these screens gain asynchronous data.

## Acceptance criteria and evidence

| ID | Observable given / when / then | Planned test or check |
| --- | --- | --- |
| AC-01 | Given any authenticated destination, when it opens, then its screen-specific skeleton covers the page before the existing content is revealed. | Manual visual check of all nine navigation destinations; layouts are static and require Android rendering to assess. |
| AC-02 | While the skeleton is visible, it blocks taps and does not add noisy accessibility content; after dismissal, existing screen interactions work. | Manual interaction/accessibility check. |
| AC-03 | Login and registration remain unchanged. | Review navigation scope and changed files. |

## Technical impact

Add a reusable presentation-only skeleton overlay and attach it to the nine authenticated fragment layouts at render time. No backend or third-party dependency is required.

## Decisions and open questions

| Item | Status | Source or decision owner |
| --- | --- | --- |
| Skeleton duration | Decided | Use a 550 ms placeholder with a 160 ms fade while these destinations have no asynchronous load state. |

## Implementation outcome

Implemented on all nine destinations. `assembleDebug` could not complete because this environment lacks the configured Eclipse Temurin 17 toolchain and network access prevents Gradle from downloading it. Visual and accessibility checks on a device remain pending.
