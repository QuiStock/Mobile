# Notifications screen

**Status:** In progress  
**Issue:** none  
**Product decision owner:** open

## Problem and goal

Users need a screen to review recent store notifications and identify which notices can lead to a follow-up action. The supplied screenshot is the visual reference.

## Scope

**In scope:** A notifications screen with the store header, back control, title, and a vertically scrollable list containing the three notification examples shown in the reference. Cards follow the existing outlined card style. The first and third cards display an `Analisar` button; the second does not. The promotion icon reuses the existing promotion drawable at a smaller size. Placeholder image views reserve the requested cart and transport icon positions.

**Out of scope:** Loading live notifications and mapping message types to icons. The cards remain static examples, so `Analisar` opens a tab rather than the specific item.

## Expected behavior

When the notifications destination opens, the screen shows the provided store and user names, a back control, the `Notificações` title, and three notification cards in a vertical scroll container. Each card shows its message and timestamp. The cart and transport image views have no drawable assigned until the corresponding assets are provided. `Analisar` controls are visible on the first and third cards.

The back control returns to the screen that opened notifications, matching the system back. `Analisar` on either card opens the Interferences tab of the user's role.

## Acceptance criteria and evidence

| ID | Observable given / when / then | Planned test or check |
| --- | --- | --- |
| AC-01 | Given the notifications destination opens, when the layout renders, then the header, title, and three cards match the supplied reference and use the existing card style. | Manual visual check; this change provides a static XML screen with no data behavior. |
| AC-02 | Given the card list contains more items than fit on screen, when the user scrolls, then the list can move vertically. | Manual interaction check after additional cards are populated. |
| AC-03 | Given the sample messages render, then `Analisar` appears on the first and third cards only. | Manual visual check. |
| AC-04 | Given notifications was opened from a tab, when the user taps the back control, then that tab is shown again. | `NavigationSessionTests.notificationsBackReturnsToTheTabThatOpenedThem` |
| AC-05 | Given the stock-risk card, when the user taps `Analisar`, then the Interferences tab of the user's role opens. | `NavigationSessionTests.analyzingStockRiskOpensEmployeeInterferences`, `ManagerBottomNavigationTests.analyzingStockRiskOpensManagerInterferences` |
| AC-06 | Given the promotion card, when the user taps `Analisar`, then the Interferences tab of the user's role opens. | `NavigationSessionTests.analyzingPromotionOpensEmployeeInterferences`, `ManagerBottomNavigationTests.analyzingPromotionOpensManagerInterferences` |

## Technical impact

Adds an XML layout, a minimal Fragment that handles the back control, string resources, and navigation destinations/actions. `Analisar` is bound in `TabContentCallbacks` and opened through `BottomNavigation`, which applies the role and session checks. No backend, persistence, or data-layer changes.

## Decisions and open questions

| Item | Status | Source or decision owner |
| --- | --- | --- |
| Cart and transport drawable assets | Open | User will provide them |
| Message-to-icon mapping | Open | User will implement in Kotlin |
| `Analisar` destination | Decided: both cards open Interferences | Requester, 2026-11-04 |
| Store/user values and live notification data | Open | Product |

## Implementation outcome

To be completed after implementation and review.
