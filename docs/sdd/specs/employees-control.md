# Employee control screen

**Status:** In progress  
**Issue:** none  
**Product decision owner:** open

## Problem and goal

Managers need a screen to view store employees and identify each person's role and start date. The supplied screenshot is the visual reference.

## Scope

**In scope:** A manager employee-control screen with the shared store/user header, title, name search field, date filter, employee registration button, scrollable employee cards, and manager bottom navigation with the Employees item active. Declare and render the existing Employees destination.
**Out of scope:** Search and filter behavior, employee registration, employee data integration, employee details destination and click navigation, and deciding which bottom navigation is shown for each account type.

## Expected behavior

When the Employees destination opens, it displays the controls and sample employee cards in the provided visual style. The card area scrolls independently below the registration button. Cards expose a clickable and focusable affordance for the future employee-detail flow; its destination and click handling remain open until that screen is defined.

## Acceptance criteria and evidence

| ID | Observable given / when / then | Planned test or check |
| --- | --- | --- |
| AC-01 | Given the Employees destination opens, when the layout renders, then the store header, search/date controls, registration button, employee cards, and manager bottom navigation appear. | Manual visual check; no static layout screenshot test is established. |
| AC-02 | Given more employee cards than fit in the available space, when the card area is scrolled, then additional cards can be reached without moving the header, filters, or registration button. | Manual scroll check. |
| AC-03 | Given an employee card is displayed, when it receives focus or touch, then it exposes a clickable/focusable affordance for the future detail flow. | Manual accessibility and touch check; navigation target remains open. |

## Technical impact

Adds the Employees fragment shell and its layouts, then uses the existing `employeeFragment` navigation destination. The screen uses static sample content and introduces no backend or account-role decision logic.

## Decisions and open questions

| Item | Status | Source or decision owner |
| --- | --- | --- |
| Employee detail destination and click behavior | Open | Employee feature owner |
| Search, date filter, and registration behavior | Open | Employee feature owner |
| Bottom navigation selection by account role | Open | Login/navigation feature owner |

## Implementation outcome

Added the employee-control screen with sample employee cards, a scrollable card area, search/date controls, registration button, and employee-active manager navigation icon. Card click destinations, control behavior, and account-role navigation remain open. `git diff --check` passed; visual validation on an emulator remains pending.
