# Loading screen

**Status:** In progress  
**Issue:** none  
**Product decision owner:** open

## Problem and goal

The application requires a dedicated full-screen loading state (`LoadingFragment` / `fragment_loading.xml`) to display during long-running background tasks, data synchronization, or screen transitions, using the app's established dark purple color palette.

## Scope

**In scope:**
- Loading screen layout (`fragment_loading.xml`) with a deep purple atmospheric gradient background (`#2A084E` to `#11061F`), a rotating dotted circular spinner, and the "loading.." text in light lavender (`#D8C9E0`).
- Dotted spinner vector drawable and animated rotation resource (`bg_loading_gradient.xml`, `ic_loading_dots.xml`, `anim_loading_spinner.xml`).
- Fragment shell (`LoadingFragment.kt`).
- Destination and actions registration in `nav_graph.xml` and `NavGraph.kt`.

**Out of scope:**
- Trigger logic and auto-dismiss thresholds (to be wired by the flow controller).

## Expected behavior

When `loadingFragment` is displayed, the background presents the app's dark purple gradient, the center displays the circular dotted spinner smoothly rotating indefinitely, and the "loading.." text appears centered underneath.

## Acceptance criteria and evidence

| ID | Observable given / when / then | Planned test or check |
| --- | --- | --- |
| AC-01 | Given the user accesses the loading screen, when rendered, then the purple gradient background, circular loading indicator, and "loading.." text appear centered on screen. | Manual visual check against design. |
| AC-02 | Given the loading indicator is displayed, when observing the UI, then the spinner animates continuously. | Visual inspection of indeterminate animation. |
| AC-03 | Given the navigation graph, when looking up `loadingFragment`, then destination and actions are registered in `nav_graph.xml` and `NavGraph.kt`. | Build verification & unit test suite. |

## Technical impact

Adds `LoadingFragment.kt`, `fragment_loading.xml`, `ic_loading_dots.xml`, `anim_loading_spinner.xml`, `bg_loading_gradient.xml`, and updates `nav_graph.xml` and `NavGraph.kt`.

## Decisions and open questions

| Item | Status | Source or decision owner |
| --- | --- | --- |
| Flow transitions triggering loading | Open | Flow owner |

## Implementation outcome

Added the loading screen layout with animated custom dotted spinner and project-themed purple gradient, fragment shell, and navigation entries.
