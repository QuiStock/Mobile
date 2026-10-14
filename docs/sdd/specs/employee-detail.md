# Employee detail and registration screen

**Status:** In progress  
**Issue:** none  
**Product decision owner:** open

## Problem and goal

Managers need to register new store employees with their full name, corporate e-mail, and assigned role, accessed from the employees control screen.

## Scope

**In scope:** An employee registration and detail screen (`EmployeeDetailFragment` / `fragment_employee_detail.xml`) containing the store header, back navigation control, title ("Cadastro"), employee avatar placeholder, input fields (Full Name, E-mail, Role), "Cadastrar" action button, and "Cancelar" option. Navigation graph destination and actions from the employee control screen to employee detail.
**Out of scope:** Form validation logic, user creation API integration, photo upload, and account persistence (to be implemented by the fragment logic owner).

## Expected behavior

When the manager clicks the "Cadastrar funcionário" button on the employees control screen, the app navigates to the employee detail/registration screen. The screen renders the header, back button, registration form fields, submit button, and cancel action according to the reference design. Clicking back or cancel returns to the employees list.

## Acceptance criteria and evidence

| ID | Observable given / when / then | Planned test or check |
| --- | --- | --- |
| AC-01 | Given the user is on the employees control screen, when clicking "Cadastrar funcionário", then the app navigates to the employee detail destination. | Navigation graph action and destination check. |
| AC-02 | Given the employee detail screen opens, when rendered, then the store header, back button, "Cadastro" title, avatar placeholder, input fields (name, email, role), "Cadastrar" button, and "Cancelar" action appear. | Manual visual check against the provided design. |
| AC-03 | Given the user clicks back or "Cancelar", when triggered, then navigation pops back to the previous screen. | Manual navigation check. |

## Technical impact

Adds `EmployeeDetailFragment` shell, `fragment_employee_detail.xml` layout, registers `employeeDetailFragment` in `nav_graph.xml`, and exposes destination and actions in `NavGraph.kt`. Does not modify business or domain layers.

## Decisions and open questions

| Item | Status | Source or decision owner |
| --- | --- | --- |
| Form validation and error messages | Open | Employee feature owner |
| API integration for creating an employee | Open | Backend / Infrastructure owner |
| Photo upload / selection behavior | Open | Design / Product owner |

## Implementation outcome

Created the `EmployeeDetailFragment` shell, designed `fragment_employee_detail.xml` adhering to the visual reference, and connected destinations and actions in `nav_graph.xml` and `NavGraph.kt`. Fragment business logic is deferred to the teammate as requested.
