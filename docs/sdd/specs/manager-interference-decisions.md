# Decisão gerencial de intervenções (aprovar/reprovar)

**Status:** In progress — unit tests and checks run; instrumented, visual, and real Core validation pending  
**Issue:** none  
**Product decision owner:** solicitante

## Problem and goal

User story provided by the requester (01/11/2026): "Como Gerente, quero analisar e decidir
solicitações de intervenção da minha loja para controlar as ações executadas no estoque."
The manager hub and detail screens existed only as static layouts
([manager-interferences.md](manager-interferences.md),
[manager-create-interferences.md](manager-create-interferences.md),
[manager-approved-interferences.md](manager-approved-interferences.md)), with the approval
endpoints listed as Open. This spec closes that gap using `api-contract.md` v3.1.0.

## Scope

**In scope:** loading the store's requests, the pending list (SENT_TO_MANAGER), decided
requests (APPROVED/REJECTED) with a status badge, review with product context, approval with
confirmation, rejection with a required justification, final-state locking, and a
confirmation screen bound to the approval data.  
**Out of scope:** manager editing of `final_promotion_valid_*`, the requester's name (absent
from the contract), ERP execution, push notifications, pagination, and Core changes.

## Expected behavior

### Contract (api-contract.md §3.2–3.3)

- `GET /actions?store_id=` with the manager's store; the client keeps SENT_TO_MANAGER,
  APPROVED, and REJECTED. Optional fields `product_name`, `justification`, and
  `decision_justification` are read; absent, blank, or non-text values mean "Não informado".
- `PATCH /actions/{id}/status` with `{"status":"APPROVED"}` or
  `{"status":"REJECTED","justification":"..."}`. REJECTED requires a nonblank justification.
  An empty approval does not send `justification`.
- **Author and timestamp:** Core derives the actor from the JWT `sub` and records the decision
  and its audit trail. The app **does not** send an author, an instant, or any identity in the
  body. This satisfies "persistência e auditoria" without trusting the device clock.
- Success requires HTTP 200 with the same `id` and the requested `status`. A different body,
  timeout, or 5xx is an uncertain result: the list is reloaded and nothing is resent
  automatically. 409 (already decided or a conflict) shows a message and reloads the Core state.
  400/403/404/401 show specific messages, with no technical text.

### UI

- **Hub:** a "A decidir" heading sits above the pending list; both scrollable lists are 200dp
  tall (about three cards visible). The Promoção/Pedido de estoque cards act as a type filter (selected state in violet;
  tap again to clear). The pending list shows the product, type, and summary
  (quantity·date or discount·period) with an "Analisar" action. "Decisões registradas" lists
  final decisions with a badge (Aguardando yellow, Aprovada green, Reprovada pink), with text
  that does not depend on color. Loading, empty (with or without a filter), and error states
  (tap to retry) are covered.
- **Detail:** product name, flow, category·stock, and expiry (from the store catalog), reason
  (`justification`), and order fields (quantity and desired date) or promotion fields (discount,
  inclusive duration, and period). The ERP check notice keeps its existing behavior.
- **Approve:** existing confirmation popup → haptic feedback → button changes to "Aprovando…"
  with both buttons disabled → confirmation screen with the persisted data. The text states
  that nothing is executed in the ERP.
- **Reject:** a bottom sheet with a required field. A blank value shows an error, a short
  shake, and focus. Confirming sends; the badge appears with an overshoot animation, the
  justification card appears, and a success snackbar is shown.
- **Locking:** a final state hides Aprovar/Reprovar and shows the decision justification and
  "Decisão final registrada…". The ViewModel and use case ignore a decision for a status other
  than SENT_TO_MANAGER and a second write while one is in flight.
- **Micro-interactions:** a scale of 0.96 on press (StateListAnimator) on buttons and cards,
  ripple, and haptics (CONFIRM/REJECT on API 30+). Durations follow the system animation scale.

## Acceptance criteria and evidence

| ID | Observable given / when / then | Planned test or check |
| --- | --- | --- |
| AC-01 | Active MANAGER sees only their store's SENT_TO_MANAGER/APPROVED/REJECTED requests, separated into pending and decided, with a type filter. | `ManagerDecisionTests` (list), `ManagerInterferencesViewModelTests` (separation/filter). |
| AC-02 | EMPLOYEE, another store, or an expired session cannot decide; nothing is written. | `ManagerDecisionTests` (role/store). |
| AC-03 | Approval sends only `status=APPROVED`; rejection sends a trimmed justification; author/instant are never sent. | `InterferenceDecisionContractTests`. |
| AC-04 | A blank rejection is blocked before the network call. | `ManagerDecisionTests`; manual check of the sheet. |
| AC-05 | Only HTTP 200 confirming id/status counts as success; 400/403/404/409/5xx map to recoverable failures. | `InterferenceDecisionContractTests`. |
| AC-06 | Repeated taps cause a single write; a final state does not accept a new decision. | `ManagerInterferencesViewModelTests`, `ManagerDecisionTests`. |
| AC-07 | Conflict/uncertainty reload the Core state; a new session generation discards the previous store's data. | `ManagerInterferencesViewModelTests`. |
| AC-08 | Missing descriptive fields appear as "Não informado"; duration is inclusive. | `InterferenceDecisionContractTests`, `RequestFormatTests`. |
| AC-09 | Badges, locking, sheet, animation, haptics, TalkBack, and large font render correctly on a device. | Manual: depends on the real Android device. Not done. |
| AC-10 | Real approval/rejection against Core persists the decision, author, and audit record. | Manual on a test environment. Not done. |

## Technical impact

Domain: `ActionDecision`, `Justification`, `ActionStatus.canDecide/decided`, and descriptive
fields on `InterferenceAction`; `ManagerDecisionUseCase`; new method
`InterferenceRepository.decide`. Data: `InterferenceApi.decide` and mapper/repository changes
(`forward` now uses `copy`). Presentation: `ManagerInterferencesViewModel` (activity-scoped),
hub/detail/confirmation fragments, `ManagerRequestDetailFragment`, `RequestText`/`ProductText`,
`DecisionMotion`, and `JustificationSheet`. Resources: animator, badge/button/sheet drawables,
`item_manager_request`, `sheet_decision_justification`, and `manager_decisions.xml`. The
detail→approved routes now remove the detail screen from the back stack. The static hub cards
remain in the XML for the demo flavor and are replaced at runtime. The write limit and the
no-replay rule of the existing transport apply.

## Decisions and open questions

| Item | Status | Source or decision owner |
| --- | --- | --- |
| Endpoint, body, and justification required to reject | Confirmed | `api-contract.md` v3.1.0 §3.3 |
| Author/timestamp derived by Core from `sub` | Confirmed | `api-contract.md` "Actor IDs ... come from the authenticated `sub`" |
| Approval without a justification field (optional per contract) | Decided by agent | Keeps the approval simple and idempotent; review with the product owner |
| Requester name in the detail view | Open | The contract does not return the requester; "Solicitante" was replaced by "Motivo" |
| Maximum justification length | Open | Not defined in the contract; no client limit |
| Decided section replaces the "Interferências ativas" title | Decided by agent | Includes rejected requests; "active" would be inaccurate |

## Implementation outcome

Implemented on 01/11/2026. Run by the agent on the final version: `spotlessCheck`, `detekt`,
`testDebugUnitTest` (350 tests, 0 failures; 16 new in `ManagerDecisionTests`,
`InterferenceDecisionContractTests`, `ManagerInterferencesViewModelTests`, and
`RequestFormatTests`), `compileDemoKotlin`, `compileDebugAndroidTestKotlin`, and
`assembleRelease`, all successful; `lintDebug` was rerun separately (no errors; it failed only
when run alongside `spotlessApply` because of temporary Spotless files). Remaining lint warnings
in the new files: `UnusedAttribute` (`accessibilityHeading`, ignored below API 28) and
`InflateParams` (bottom sheet content, the same pattern as `PopupFeedback`). Not run:
`connectedDebugAndroidTest`, combined JaCoCo, AC-09 (visual/assistive on a device), and AC-10
(real Core). Gates and exclusions unchanged.
