# Employee profile screen

**Status:** In progress  
**Issue:** none  
**Product decision owner:** open

## Problem and goal

Store managers need a dedicated view to inspect an employee's detailed profile information, such as full name, email, role, and admission date, with the option to edit the profile.

## Scope

**In scope:** An employee profile screen (`EmployeeProfileFragment` / `fragment_employee_profile.xml`) displaying the store header, back navigation button, central avatar, employee name title ("Bruno Alves"), identification card (Full Name, E-mail), role card (Role, Admission Date), and an "Editar" action button. Navigation actions from the employees control screen and towards employee editing.
**Out of scope:** Edit flow execution, backend data persistence, and profile picture editing (to be completed by the fragment logic owner).

## Expected behavior

When clicking on an employee card from the employee control screen, navigation opens the employee profile screen. The screen renders the employee details according to the visual reference. Clicking back returns to the employee list, and clicking "Editar" navigates to the edit/detail screen.

## Acceptance criteria and evidence

| ID | Observable given / when / then | Planned test or check |
| --- | --- | --- |
| AC-01 | Given the user is on the employees control screen, when selecting an employee card, then navigation leads to `employeeProfileFragment`. | Navigation graph action check. |
| AC-02 | Given the employee profile screen opens, when rendered, then the store header, back button, avatar, employee name, identification card, role card, and "Editar" button appear. | Manual visual check against design. |
| AC-03 | Given the user clicks "Editar", when triggered, then navigation leads to `employeeDetailFragment`. | Navigation graph action check. |

## Technical impact

Adds `EmployeeProfileFragment` shell, `fragment_employee_profile.xml` layout, and updates `nav_graph.xml` and `NavGraph.kt`. No changes to domain or infrastructure layers.

## Decisions and open questions

| Item | Status | Source or decision owner |
| --- | --- | --- |
| Dynamic data population | Open | Employee feature owner |
| Edit action routing and form prefilling | Open | Employee feature owner |

## Implementation outcome

Added the `EmployeeProfileFragment` shell, `fragment_employee_profile.xml` layout, registered navigation routes in `nav_graph.xml` and `NavGraph.kt`. Logic deferred to teammate as requested.
