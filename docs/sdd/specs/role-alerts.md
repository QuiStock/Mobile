# Role-based alerts (device notifications)

**Status:** In progress  
**Issue:** none  
**Product decision owner:** requester (answers of 02/11/2026)

## Problem and goal

User story: "Como Repositor ou Gerente, quero receber alertas relevantes ao meu papel para agir no
momento adequado sem acompanhar telas continuamente."

Acceptance criteria received:

1. O responsável recebe alerta de produto próximo do vencimento.
2. O Gerente recebe alerta quando há solicitação aguardando aprovação.
3. O Repositor recebe alerta quando sua solicitação é aprovada.
4. Cada alerta abre o contexto correto e não expõe dados fora da permissão.
5. Preferências e duplicidade de entrega são tratadas conforme as regras definidas.

## Known constraints (verified)

- Core contract v3.1.0 (`DS_Backend/api-contract.md`) has no push, device-token or notification
  endpoint, and the requester said Core must not change. The app has no FCM dependency.
- `GET /actions?store_id=` lists the store's actions with `id`, `status`, `action_type`,
  `product_id`, `product_name`; it does **not** expose who created the action.
- `GET /flows/current` + `/products` (already used by Home) expose the `NEAR_EXPIRY` risk:
  positive stock and `expiration_days` between 0 and 7 inclusive.
- EMPLOYEE may read all store actions (`GET /actions` is store-scoped); MANAGER decides
  SENT_TO_MANAGER actions of its own store.

## Decisions (requester, 02/11/2026)

| Item | Decision |
| --- | --- |
| Delivery | Local polling: WorkManager periodic work (15 min, Android minimum, network required) reads Core and posts local notifications. Alerts are therefore delayed by up to the polling period plus OS deferral, and only arrive while a stored session can be renewed. |
| "Sua solicitação" without Core author | Track locally: when the logged-in EMPLOYEE successfully forwards a request (`SENT_TO_MANAGER` confirmed by Core) from this device, its action ID is recorded under that Core user ID. Only recorded IDs that become `APPROVED` alert. Requests forwarded from another device or before reinstalling/clearing data are not alerted. |
| "O responsável" / "próximo do vencimento" | EMPLOYEE and MANAGER of the store; Core `NEAR_EXPIRY` risk (EXPIRED excluded, as on Home). |
| Preferences | One switch per alert type applicable to the role, in Profile → "Alertas", all on by default, stored per Core user on the device. Android notification channels (one per type) also let the user mute in system settings. |
| Duplicates | Each event is delivered at most once per user and device: near expiry once per product per business day (America/Sao_Paulo); pending approval once per action ID; approval once per action ID. |

## Expected behavior

### Role matrix

| Alert | EMPLOYEE | MANAGER | Opens |
| --- | --- | --- | --- |
| Near expiry (`NEAR_EXPIRY`) | yes | yes | Home (priority alerts of the store, near expiry listed first) |
| Request awaiting approval (`SENT_TO_MANAGER`) | no | yes | Manager interferences hub, then the request's order/promotion detail when it is still pending |
| My request approved (`APPROVED`, locally owned) | yes | no | Employee interferences hub (store requests list) |

REGIONAL_MANAGER and other roles never reach a Ready profile on mobile and get no alerts.

### Check cycle

1. Runs on login/profile ready (immediately) and then periodically in the background.
2. Skipped entirely, with no Core call, when notifications are not allowed (Android 13+ permission
   denied or app notifications disabled) or every applicable preference is off.
3. In a cold process the session is restored with the stored refresh credential, exactly as on app
   start; the profile is then loaded. Without an Active session and a Ready EMPLOYEE/MANAGER profile
   with a store, nothing is read or posted.
4. Products/analyses and actions are read with the profile's `store_id` (Core enforces scope).
   A failed read only skips that alert source in this cycle; nothing is marked delivered.
5. New events per type: up to 3 are posted individually; 4 or more become one summary notification
   ("N produtos próximos do vencimento", "N solicitações aguardando aprovação",
   "N solicitações aprovadas") that opens the list context.
6. Delivered/owned memory is pruned: near-expiry keys of previous days, pending keys of actions no
   longer SENT_TO_MANAGER, owned IDs once APPROVED (alerted) or REJECTED (no alert).
7. A disabled type is not notified and its current events are marked as seen, so re-enabling does
   not replay a backlog. Exception: while every type of the role is off, Core is not read at all, so
   events current at re-enable time are delivered once.

### Session end and permission

- Logout, expiry, local failure or signed-out state cancel the periodic work and remove posted
  notifications. A background cycle that finds the session expired/signed-out does the same.
- Delivered/owned memory and preferences stay per Core user ID; another account on the same device
  uses its own memory and never sees the previous user's notifications.
- On the first Ready profile on Android 13+, the system notification permission is requested once.
  Denial does not block the app; Profile shows "Permitir notificações" which opens the system dialog
  again, or the app's notification settings after a permanent denial.

### Privacy and permission on open

- Notifications use `VISIBILITY_PRIVATE` with a public version without product names
  ("Novo alerta do QuiStock"). Content contains only the product name, action type and count.
- The PendingIntent carries only the alert type, target ID and the Core user ID it was produced for.
  On tap, the app opens the context only if the current session is Active and the Ready profile is the
  same user with a role that receives that type; otherwise the normal session flow is shown (login or
  Home) and the target is discarded. The detail screen data is always re-read from Core.

## Acceptance criteria and evidence

| ID | Observable given / when / then | Planned test or check |
| --- | --- | --- |
| RA-01 | Given an EMPLOYEE or MANAGER and a product with NEAR_EXPIRY (and not EXPIRED), when a cycle runs, then one near-expiry alert is posted for that product. | `RoleAlertsUseCaseTests` |
| RA-02 | Given a MANAGER and a SENT_TO_MANAGER action, when a cycle runs, then a pending-approval alert is posted; an EMPLOYEE gets none. | `RoleAlertsUseCaseTests` |
| RA-03 | Given an EMPLOYEE who forwarded action X from this device, when X becomes APPROVED, then one approval alert is posted; approvals of actions not owned, or REJECTED, post nothing; MANAGER gets none. | `RoleAlertsUseCaseTests`, `InterferenceWorkflowTests` (forward records ownership) |
| RA-04 | Given an event already delivered, when the next cycle runs, then it is not posted again; near expiry is posted again on the next business day. | `RoleAlertsUseCaseTests` |
| RA-05 | Given a disabled type, when a cycle runs, then it is not posted and is not replayed after re-enabling. | `RoleAlertsUseCaseTests` |
| RA-06 | Given notifications not allowed, no Active session, or a non-Ready profile, when a cycle runs, then Core is not read and nothing is posted. | `RoleAlertsUseCaseTests` |
| RA-07 | Given 4+ new events of a type, then one summary notification is posted for that type. | `RoleAlertsUseCaseTests` |
| RA-08 | Given an alert tap, when the current session/profile is the same user with a role entitled to the type, then the context opens; otherwise the target is discarded. | `AlertTargetTests` (entitlement); navigation itself is a manual device check |
| RA-09 | Given a read failure, then that source is skipped and nothing is marked delivered. | `RoleAlertsUseCaseTests` |
| RA-10 | Given logout/expiry, then the periodic work is cancelled and notifications are removed. | `RoleAlertsUseCaseTests` (`stop`), manual device check for WorkManager/NotificationManager |
| RA-11 | Lock screen shows only the generic public version. | Manual device check |
| RA-12 | Profile shows only the switches applicable to the role, persisted per user. | `RoleAlertsUseCaseTests` (preferences per user/role); visual check manual |

## Technical impact

- New dependency `androidx.work:work-runtime-ktx`. Manifest: `POST_NOTIFICATIONS`.
- Domain: `RoleAlert` model, `RoleAlertsUseCase`, ports `AlertMemoryStore`, `AlertNotifier`,
  `AlertScheduler`, `RequestOwnership`.
- Data: DataStore-backed memory (`quistock_role_alerts`), Android notifier (channels per type),
  WorkManager scheduler and worker.
- Presentation: `SessionViewModel` starts/stops alerts; `MainActivity` handles taps and the one-time
  permission request; Profile "Alertas" section; manager hub accepts a focus action.
- Risks: polling delay; background refresh rotates the refresh credential like a normal app start;
  battery/Doze can defer work; ownership limited to this device.

## Decisions and open questions

| Item | Status | Source or decision owner |
| --- | --- | --- |
| Real-time push (FCM + Core events/author) | Open — would replace polling/local ownership | Backend/product |
| Alert for REJECTED requests | Not requested; no alert | Requester |
| Polling period | 15 min (Android minimum) | Agent proposal accepted with "Proposed defaults" |

## Implementation outcome

Implemented on 02/11/2026.

- Domain: `RoleAlert`/`AlertTarget`/`AlertMemory` (`domain/model/RoleAlert.kt`), ports in
  `domain/port/RoleAlertPorts.kt` and `NotificationPromptPreferences`, rules in `RoleAlertEvents`,
  orchestration in `RoleAlertsUseCase`. `CreateInterferenceUseCase.forward` records ownership after a
  Core-confirmed SENT_TO_MANAGER; a storage failure never changes the forward result.
- Data: `DataStoreAlertMemoryStore` (`quistock_role_alerts`, JSON per Core user ID, no product names),
  `WorkManagerAlertScheduler`/`RoleAlertsWorker` (unique periodic work, 15 min, network required,
  failures wait for the next period), `AndroidNotificationPromptPreferences`.
- Presentation: `AndroidAlertNotifier` (channel per type, `VISIBILITY_PRIVATE` + generic public
  version, PendingIntent with type/user/target only), `AlertTargetIntent`, `AlertLauncher`
  (entitlement + wait/discard), `NotificationPermissionPrompt` (asked once on the first Ready profile),
  `SessionAlerts` in `SessionViewModel` (start per generation, stop on session end), Profile "Alertas"
  section (`view_role_alerts.xml`, `AlertPreferencesViewModel`), manager hub focus
  (`ManagerInterferencesViewModel.focus/takeFocus`).
- Dependency: `androidx.work:work-runtime-ktx` 2.10.1. Manifest: `POST_NOTIFICATIONS`.

Evidence (run by the agent on 02/11/2026, Windows, local Gradle):

- `testDebugUnitTest`: 89 suites, 440 tests, 0 failures/errors. New/changed suites:
  `RoleAlertsUseCaseTests` (15), `InterferenceWorkflowTests` (8, +2 ownership),
  `ManagerInterferencesViewModelTests` (6, +1 focus), `AlertPreferencesViewModelTests` (2),
  `DataStoreAlertMemoryStoreTests` (1), `AppModulesTest` (2, Koin graph incl. the new module).
- `spotlessCheck`, `detekt`, `lintDebug` (no errors), `assembleRelease`, `compileDebugAndroidTestKotlin`: success.
- Not run: `connectedDebugAndroidTest`, combined JaCoCo gate, and the manual device checks of RA-08
  (navigation on tap), RA-10 (WorkManager/NotificationManager), RA-11 (lock screen) and RA-12 (visual).
  Real Core polling was not exercised.
