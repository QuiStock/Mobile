# Profile screen

**Status:** In progress  
**Issue:** none  
**Product decision owner:** open

## Problem and goal

Store operators need a profile page to access account, store, and help areas and edit or leave their account. The supplied screenshot is the visual reference.

## Scope

**In scope:** A profile screen with the shared store/user header, back control, profile image and user details, three navigation rows, edit-profile button, sign-out action, and a route from Home to Profile.  
**Out of scope:** Implementing click handlers or destination pages for account, store, help, edit profile, and sign out; these require product destinations and Kotlin behavior not provided in this request.

## Expected behavior

When the profile destination opens, it displays the provided profile content and visual style. The header back control has a route to Home. The three rows are separate focusable controls with stable IDs for future navigation handlers. Profile edit and sign-out controls are also exposed with stable IDs; their behavior remains open.

## Acceptance criteria and evidence

| ID | Observable given / when / then | Planned test or check |
| --- | --- | --- |
| AC-01 | Given the profile destination opens, when the layout renders, then the store header, profile details, account/store/help rows, and edit/sign-out controls appear in the supplied style. | Manual visual check; no screenshot test is established for static layouts. |
| AC-02 | Given Home is active, when the profile destination is requested, then navigation can open Profile; the back route returns to Home. | Inspect the Navigation graph; manual navigation check after handlers are wired. |

## Technical impact

Adds a profile layout, minimal fragment shell, profile strings, and Home/Profile navigation declarations. No backend or persistence changes.

## Decisions and open questions

| Item | Status | Source or decision owner |
| --- | --- | --- |
| Destinations and click behavior for account, store, help, edit profile, and sign out | Open | Product / feature owner |

## Implementation outcome

To be completed after implementation and review.
