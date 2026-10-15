# Manager approved interferences screens (Promotion and Order approval confirmation)

**Status:** In progress  
**Issue:** none  
**Product decision owner:** open

## Problem and goal

Store managers need confirmation screens upon approving or completing interference requests (promotion created/approved and stock order sent/approved), providing clear feedback, status overview, and a straightforward return route back to the manager interferences hub.

## Scope

**In scope:**
- Promotion approved screen (`ManagerPromoApprovedFragment` / `fragment_manager_promo_approved.xml`) with success confirmation icon, title ("Promoção aprovada"), descriptive subtitle, summary table (product, period, status "Ativo"), and "Voltar para interferências" button, structured based on `fragment_promo_sent.xml`.
- Order approved screen (`ManagerOrderApprovedFragment` / `fragment_manager_order_approved.xml`) with success confirmation icon, title ("Pedido aprovado"), descriptive subtitle, summary table (order number, quantity, delivery forecast), and "Voltar para Interferências" button, structured based on `fragment_order_sent.xml`.
- Registration of destinations and navigation routes in `nav_graph.xml` and `NavGraph.kt`.

**Out of scope:**
- Backend confirmation hooks and reactive status refresh (to be implemented by the business logic owner).

## Expected behavior

Navigating to `managerPromoApprovedFragment` or `managerOrderApprovedFragment` displays the confirmation layout centered gracefully on the dark theme background (`#11061F`), displaying confirmation iconography, respective summary metadata, and a navigation button returning to `managerInterferencesFragment`.

## Acceptance criteria and evidence

| ID | Observable given / when / then | Planned test or check |
| --- | --- | --- |
| AC-01 | Given the user accesses the manager promotion approved screen, when rendered, then the confirmation icon, title, description, product summary table (product, period, status), and return button appear. | Manual visual check against design and `fragment_promo_sent.xml` baseline. |
| AC-02 | Given the user accesses the manager order approved screen, when rendered, then the confirmation icon, title, description, order summary table (order, quantity, forecast), and return button appear. | Manual visual check against design and `fragment_order_sent.xml` baseline. |
| AC-03 | Given the navigation graph, when looking up promotion and order approved destinations and actions, then `managerPromoApprovedFragment` and `managerOrderApprovedFragment` are correctly registered in `nav_graph.xml` and `NavGraph.kt`. | Build verification & unit test suite. |

## Technical impact

Adds `ManagerPromoApprovedFragment`, `ManagerOrderApprovedFragment`, `fragment_manager_promo_approved.xml`, `fragment_manager_order_approved.xml`, and updates `nav_graph.xml` and `NavGraph.kt`.

## Decisions and open questions

| Item | Status | Source or decision owner |
| --- | --- | --- |
| Dynamic data binding for confirmation summary values | Open | Feature owner |

## Implementation outcome

Added the layout files and fragment shells for manager approved promotion and order screens based on `fragment_promo_sent.xml` and `fragment_order_sent.xml`, and registered destinations and actions in `nav_graph.xml` and `NavGraph.kt`.
