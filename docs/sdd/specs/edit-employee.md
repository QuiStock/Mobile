# Edit employee screen

**Status:** In progress  
**Issue:** none  
**Product decision owner:** open

## Problem and goal

Store managers require an interface to edit existing employee records, updating their personal information, contact email, and operational roles when organizational changes occur.

## Scope

**In scope:**
- Edit employee screen (`EditEmployeeFragment` / `fragment_edit_employee.xml`) displaying the store header, back button ("Voltar"), screen title ("Editar funcionário"), employee avatar icon, prefilled editable form fields (Full name "Breno Almeida Alves", Email "bruno.alves@quistock.com.", Role "Estoquista"), primary action button ("Salvar alterações"), and cancel action ("Cancelar").
- Registration of the destination `editEmployeeFragment` and navigation actions in `nav_graph.xml` and `NavGraph.kt`.

**Out of scope:**
- Persistence, data validation, and backend API integration for updating employee data (to be implemented by the business logic owner).

## Expected behavior

Navigating to `editEmployeeFragment` renders the form with current employee data prefilled in the input fields so that the user can modify only what is needed. Clicking the back button or "Cancelar" returns to the employee profile.

## Acceptance criteria and evidence

| ID | Observable given / when / then | Planned test or check |
| --- | --- | --- |
| AC-01 | Given the user navigates to edit employee, when the screen renders, then the header, back button, title "Editar funcionário", avatar, and prefilled fields (name, email, role) appear according to the design. | Manual visual check against design. |
| AC-02 | Given the edit employee screen is displayed, when observing action controls, then "Salvar alterações" and "Cancelar" are present and interactive. | Manual visual inspection. |
| AC-03 | Given the navigation graph, when looking up `editEmployeeFragment` destination and actions, then they are registered in `nav_graph.xml` and `NavGraph.kt`. | Build verification & unit test suite. |

## Technical impact

Adds `EditEmployeeFragment`, `fragment_edit_employee.xml`, `edit-employee.md` spec, and updates `nav_graph.xml` and `NavGraph.kt`. Does not modify business or domain layers.

## Decisions and open questions

| Item | Status | Source or decision owner |
| --- | --- | --- |
| Dynamic employee data passing via SafeArgs | Open | Feature owner |
| Update employee API endpoint | Open | Backend owner |

## Implementation outcome

Created `EditEmployeeFragment` shell, `fragment_edit_employee.xml` layout with prefilled values, and registered routes in `nav_graph.xml` and `NavGraph.kt`.
