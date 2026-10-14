# Manager interferences screen

**Status:** In progress  
**Issue:** none  
**Product decision owner:** open

## Problem and goal

Store managers require a dedicated overview screen to monitor and analyze store interferences, such as pending promotion and stock replenishment requests, along with tracking active running interferences, with full manager navigation.

## Scope

**In scope:** A manager interferences screen (`ManagerInterferencesFragment` / `fragment_manager_interferences.xml`) featuring the store header, title ("Interferências"), shortcut cards (Promoção, Pedido de estoque), pending request cards with "Analisar" action triggers, active interferences listing (Promoção Verão 25%, Pedido #1014 - Refrigerantes), and the 5-item manager bottom navigation bar with the Interferences tab highlighted. Navigation destination and routes in `nav_graph.xml` and `NavGraph.kt`.
**Out of scope:** Backend interference data integration, pending request analysis execution, and active item status modifications (to be implemented by the fragment logic owner).

## Expected behavior

When navigating to the manager interferences destination, the screen renders all shortcuts, pending requests, active items, and manager bottom navigation according to the visual reference design. Clicking bottom bar items or analysis actions triggers navigation to their respective targets.

## Acceptance criteria and evidence

| ID | Observable given / when / then | Planned test or check |
| --- | --- | --- |
| AC-01 | Given the user accesses the manager interferences screen, when rendered, then the store header, shortcuts, pending request cards, active list, and manager bottom navigation appear. | Manual visual check against design. |
| AC-02 | Given the manager interferences screen is open, when scrolling through items, then content scrolls inside the ScrollView without breaking the header or bottom navigation bar. | Manual scroll check. |
| AC-03 | Given manager bottom navigation is rendered, when observing the active tab, then the interferences icon displays the active state. | Visual inspection of `ic_interferences_active`. |

## Technical impact

Adds `ManagerInterferencesFragment` shell, `fragment_manager_interferences.xml` layout, and updates `nav_graph.xml` and `NavGraph.kt`. Does not modify business or domain layers.

## Decisions and open questions

| Item | Status | Source or decision owner |
| --- | --- | --- |
| Dynamic listing data source | Open | Backend / Infrastructure owner |
| Action navigation when clicking "Analisar" | Open | Feature owner |

## Implementation outcome

Added the `ManagerInterferencesFragment` shell, `fragment_manager_interferences.xml` layout, and registered routes in `nav_graph.xml` and `NavGraph.kt`. Fragment business logic is deferred to teammate as requested.
