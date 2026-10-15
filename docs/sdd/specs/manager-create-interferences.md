# Manager create interferences screens (Promotion and Order details)

**Status:** In progress  
**Issue:** none  
**Product decision owner:** open

## Problem and goal

Store managers require dedicated review and decision screens when evaluating pending interference actions—specifically promotion details and replenishment order details—to either approve or reject suggested stock and promotional actions.

## Scope

**In scope:**
- Promotion details screen (`ManagerPromotionDetailFragment` / `fragment_manager_promotion_detail.xml`) containing store header, back navigation button, product information card (name, low flow tag, category, expiration details), suggested discount, duration, requester, and approve/reject buttons.
- Order details screen (`ManagerOrderDetailFragment` / `fragment_manager_order_detail.xml`) containing store header, back navigation button, product information card, suggested quantity, desired date, and approve/reject buttons.
- Registration of both destinations and navigation actions in `nav_graph.xml` and `NavGraph.kt`.

**Out of scope:**
- Backend API integration and click handling logic for approval/rejection (to be implemented by the business logic owner).

## Expected behavior

Navigating to `managerPromotionDetailFragment` or `managerOrderDetailFragment` renders the respective screen matching Figma designs with consistent dark theme styling (`#11061F`), store header, back button, product summary card, parameter details, and approval action buttons.

## Acceptance criteria and evidence

| ID | Observable given / when / then | Planned test or check |
| --- | --- | --- |
| AC-01 | Given the user accesses the manager promotion detail screen, when rendered, then the store header, back button, product card, discount/duration details, and approve/reject buttons appear. | Manual visual check against design. |
| AC-02 | Given the user accesses the manager order detail screen, when rendered, then the store header, back button, product card, quantity/date details, and approve/reject buttons appear. | Manual visual check against design. |
| AC-03 | Given the navigation graph, when looking up promotion and order detail destinations and actions, then `managerPromotionDetailFragment` and `managerOrderDetailFragment` are correctly registered in `nav_graph.xml` and `NavGraph.kt`. | Build verification & unit test suite. |

## Technical impact

Adds `ManagerPromotionDetailFragment`, `ManagerOrderDetailFragment`, `fragment_manager_promotion_detail.xml`, `fragment_manager_order_detail.xml`, and updates `nav_graph.xml` and `NavGraph.kt`. Does not alter existing data or domain models.

## Decisions and open questions

| Item | Status | Source or decision owner |
| --- | --- | --- |
| Dynamic data source for product parameters | Open | Backend / Infrastructure owner |
| Approval and rejection network endpoints | Open | Feature owner |

## Implementation outcome

Added the layout files and fragment shells for manager promotion and order details, registered navigation destinations and actions in `nav_graph.xml` and `NavGraph.kt`. Business logic implementation left for the teammate as requested.
