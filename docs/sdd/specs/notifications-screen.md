# Notifications screen

**Status:** In progress  
**Issue:** none  
**Product decision owner:** open

## Problem and goal

Users need a screen to review recent store notifications and identify which notices can lead to a follow-up action. The supplied screenshot is the visual reference.

## Scope

**In scope:** A notifications screen with the store header, back control, title, and a vertically scrollable list containing the three notification examples shown in the reference. Cards follow the existing outlined card style. The first and third cards display an `Analisar` button; the second does not. The promotion icon reuses the existing promotion drawable at a smaller size. Placeholder image views reserve the requested cart and transport icon positions.

**Out of scope:** Loading live notifications, mapping message types to icons, and implementing button or back click handlers. These require Kotlin work the user plans to complete separately.

## Expected behavior

When the notifications destination opens, the screen shows the provided store and user names, a back control, the `Notificações` title, and three notification cards in a vertical scroll container. Each card shows its message and timestamp. The cart and transport image views have no drawable assigned until the corresponding assets are provided. `Analisar` controls are visible on the first and third cards. Navigation routes are declared for opening notifications from Home and returning to Home; click handlers remain open.

## Acceptance criteria and evidence

| ID | Observable given / when / then | Planned test or check |
| --- | --- | --- |
| AC-01 | Given the notifications destination opens, when the layout renders, then the header, title, and three cards match the supplied reference and use the existing card style. | Manual visual check; this change provides a static XML screen with no data behavior. |
| AC-02 | Given the card list contains more items than fit on screen, when the user scrolls, then the list can move vertically. | Manual interaction check after additional cards are populated. |
| AC-03 | Given the sample messages render, then `Analisar` appears on the first and third cards only. | Manual visual check. |

## Technical impact

Adds an XML layout, a minimal Fragment that loads the layout, string resources, and navigation destinations/actions. No backend, persistence, or data-layer changes.

## Decisions and open questions

| Item | Status | Source or decision owner |
| --- | --- | --- |
| Cart and transport drawable assets | Open | User will provide them |
| Message-to-icon mapping and `Analisar` behavior | Open | User will implement in Kotlin |
| Store/user values and live notification data | Open | Product |

## Implementation outcome

To be completed after implementation and review.
