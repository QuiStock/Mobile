# Profile screen

**Status:** In progress  
**Issue:** none  
**Product decision owner:** open

## Problem and goal

Store operators need a profile page to access account, store, and help areas and edit or leave their account. The supplied screenshot is the visual reference.

## Scope

**In scope:** A profile screen with the shared store/user header, back control, profile image and user details, three navigation rows, edit-profile button, sign-out action, and a route from Home to Profile. The edit-profile screen contains a profile image/photo control, full-name and email fields, save and cancel controls, and a route from Profile.
**Out of scope:** Camera launch, persistence/validation for edited values, and click handlers for profile actions; these require Kotlin behavior owned by the feature implementer.

## Expected behavior

When the profile destination opens, it displays the provided profile content and visual style. The edit-profile destination displays the provided form fields and controls. The photo control is identified for the camera handler, which is implemented separately. Profile/edit navigation routes are declared, with stable IDs for future click handlers.

## Acceptance criteria and evidence

| ID | Observable given / when / then | Planned test or check |
| --- | --- | --- |
| AC-01 | Given the profile destination opens, when the layout renders, then the store header, profile details, account/store/help rows, and edit/sign-out controls appear in the supplied style. | Manual visual check; no screenshot test is established for static layouts. |
| AC-02 | Given Home is active, when the profile destination is requested, then navigation can open Profile; the back route returns to Home. | Inspect the Navigation graph; manual navigation check after handlers are wired. |
| AC-03 | Given Profile is active, when the edit-profile destination is requested, then the screen shows the photo control, full-name and email fields, save, and cancel controls. | Manual visual check; camera and save behavior are outside this scope. |

## Technical impact

Adds profile and edit-profile layouts, minimal fragment shells, strings, and navigation declarations. No backend, camera, or persistence changes.

## Decisions and open questions

| Item | Status | Source or decision owner |
| --- | --- | --- |
| Destinations and click behavior for account, store, help, edit profile, save/cancel, and sign out | Open | Product / feature owner |
| Camera launch for the photo control | Open | Kotlin feature implementer |

## Implementation outcome

To be completed after implementation and review.
