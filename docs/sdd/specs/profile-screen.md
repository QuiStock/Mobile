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

Follow-up approved by the requester on 2026-10-21: in Edit Profile, Cancel pops the current navigation entry and returns to the previous screen without confirmation. Save stays on the form and shows “Salvar alterações ainda não foi implementado.” using the existing status popup. Neither action saves values or calls the API. Other unspecified actions remain outside this follow-up.

When the profile destination opens, it displays the provided profile content and visual style. The edit-profile destination displays the provided form fields and controls. The photo control is identified for the camera handler, which is implemented separately. Profile/edit navigation routes are declared, with stable IDs for future click handlers.

Follow-up approved by the requester on 2026-10-16: tapping the Home header photo (`btnPerfil`) opens Profile through the existing action. The Profile back control returns to the existing Home destination without adding another Home entry. Repeated taps after leaving Home must not navigate again. This follow-up wires only this entry and return path; other unspecified profile actions remain outside its scope.

## Acceptance criteria and evidence

| ID | Observable given / when / then | Planned test or check |
| --- | --- | --- |
| AC-01 | Given the profile destination opens, when the layout renders, then the store header, profile details, account/store/help rows, and edit/sign-out controls appear in the supplied style. | Manual visual check; no screenshot test is established for static layouts. |
| AC-02 | Given Home is active, when the header photo is tapped, then Profile opens; tapping its back control returns to the existing Home entry. | Instrumented `ProfileLogoutTests.headerPhotoOpensProfileAndBackReturnsToExistingHome`; logout tests also enter through the photo. Written, not executed. |
| AC-03 | Given Profile is active, when the edit-profile destination is requested, then the screen shows the photo control, full-name and email fields, save, and cancel controls. | Manual visual check; camera and save behavior are outside this scope. |
| AC-04 | Given Edit Profile is open, when Cancel is tapped, then return to the previous navigation entry without saving or confirmation. | Manual navigation check by requester; simple click wiring does not warrant a new automated test. |
| AC-05 | Given edited or unchanged form values, when Save is tapped, then show the not-implemented notice, remain on the form and retain the current inputs without saving. | Manual check by requester; simple placeholder feedback does not warrant a new automated test. |

## Technical impact

Adds profile and edit-profile layouts, minimal fragment shells, strings, and navigation declarations. No backend, camera, or persistence changes.

## Decisions and open questions

| Item | Status | Source or decision owner |
| --- | --- | --- |
| Cancel and temporary Save behavior in Edit Profile | Closed | Requester, 2026-10-21; AC-04 and AC-05 |
| Other profile actions | See related specs / open where unspecified | Product / feature owner |
| Camera launch for the photo control | Open | Kotlin feature implementer |

## Implementation outcome

2026-10-21 follow-up: Edit Profile binds its buttons with ViewBinding. Cancel pops only while Edit Profile is the current destination; Save uses `PopupFeedback` and a string resource. No API or persistence changes. Tests and validations were not run at the requester's instruction; AC-04/AC-05 require manual confirmation.

To be completed after implementation and review.

2026-10-16 follow-up: connected the Home photo and Profile back control, using ViewBinding and the existing Navigation graph. Updated logout instrumentation to use the actual entry control. Runtime checks and Gradle were not executed, following the requester's standing instruction; unrelated profile decisions remain open.
