# FAQ and contact screens

**Status:** In progress  
**Issue:** none  
**Product decision owner:** open

## Problem and goal

Store operators need a place to find help and a form to contact the QuiStock team. The supplied screenshot is the visual reference.

## Scope

**In scope:** A FAQ screen with the four questions shown in the reference, independent expandable cards with up/down arrows, and an underlined link to a contact form. The contact screen has full-name, email, problem, and message fields plus a send control. Navigation connects Profile to FAQ, FAQ to Contact, and back to the previous screen.  
**Out of scope:** Sending or persisting the contact form, and backend integration.

## Expected behavior

FAQ cards start collapsed with a down arrow. Selecting a card reveals its supplied answer and changes the arrow to up; selecting it again collapses it. Selecting the underlined contact link opens the form. The contact form displays the fields and send control in the supplied style; submission behavior is not defined.

## Acceptance criteria and evidence

| ID | Observable given / when / then | Planned test or check |
| --- | --- | --- |
| AC-01 | Given the FAQ screen opens, then all four questions are visible as collapsed cards with down arrows. | Manual visual check. |
| AC-02 | Given a FAQ card is collapsed, when it is tapped, then its answer area is revealed and its arrow points up; tapping again collapses it. | Instrumented `FaqFragmentTests` checks all four answers through expand/collapse; verify arrow artwork manually. |
| AC-03 | Given the FAQ screen is open, when the underlined contact link is tapped, then the contact form opens. | Instrumented `FaqFragmentTests` checks FAQ/Contact/Profile navigation destinations. |
| AC-04 | Given the contact screen opens, then name, email, problem, and message fields and a send control are visible. | Instrumented `FaqFragmentTests` checks all form controls are displayed; verify visual styling manually. |

## Technical impact

Adds FAQ/contact layouts and fragments, strings, and navigation declarations. The profile help control routes to FAQ. No persistence, network, or camera changes.

## Decisions and open questions

| Item | Status | Source or decision owner |
| --- | --- | --- |
| Contact form submission behavior and destination | Open | Product / feature owner |

## Implementation outcome

To be completed after implementation and review.
