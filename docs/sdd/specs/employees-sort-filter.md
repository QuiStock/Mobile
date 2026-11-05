# Ordenação e limpeza do filtro no controle de funcionários

**Status:** In progress  
**Issue:** none  
**Product decision owner:** solicitante da branch `fix/login-screen`

## Problem and goal

Na tela de controle de funcionários, escolher "Mais recentes", "Mais antigos" ou "Ordem alfabética (A-Z)" só trocava o texto do campo: a lista não mudava. Depois de escolher uma opção, também não havia como voltar ao estado sem filtro. O solicitante pediu que o filtro funcione e que um "X" dentro do campo permita cancelá-lo.

## Scope

**In scope:** aplicar as três ordenações à lista, combiná-las com a busca por nome e oferecer um "X" no campo para remover a seleção.  
**Out of scope:** alterações no Core, exibição de data nos cards, troca do texto "Filtrar por data" e o sabor demo, que continua apenas com a troca de texto.

## Expected behavior

- Sem seleção, o campo mostra "Filtrar por data" com a seta, e a lista segue a ordem recebida do Core.
- Ao escolher uma opção, o campo mostra o nome dela, a seta dá lugar ao "X" e a lista é reordenada imediatamente.
- Ao tocar no "X", a seleção é removida e o campo e a lista voltam ao estado sem seleção. Tocar no restante do campo continua abrindo o menu.
- "Mais recentes" e "Mais antigos" usam o `id` numérico como indicador da ordem de cadastro. `GET /team-members` não expõe data (contrato v3.1.0) e o Core não pode mudar; o `id` vem de uma coluna `IDENTITY`, crescente na criação. Um `id` não numérico fica por último nas duas direções.
- "Ordem alfabética (A-Z)" compara nomes em pt-BR, sem diferenciar maiúsculas de minúsculas, e posiciona nomes acentuados junto às letras base.
- A busca por nome se aplica junto com a ordenação.
- A seleção sobrevive à recriação da tela e é mantida ao recarregar a lista, porque fica no `TeamViewModel`.
- O "X" é anunciado como "Remover filtro" por leitores de tela.

## Acceptance criteria and evidence

| ID | Observable given / when / then | Planned test or check |
| --- | --- | --- |
| AC-01 | Dada a lista carregada, quando o gerente escolhe "Mais recentes", então os maiores `id` numéricos aparecem primeiro. | `TeamSortTests.newestListsTheHighestNumericIdFirst`, `EmployeeSortTests.choosingAndClearingASortReordersTheList` |
| AC-02 | Dada a lista carregada, quando o gerente escolhe "Mais antigos", então os menores `id` numéricos aparecem primeiro. | `TeamSortTests.oldestListsTheLowestNumericIdFirst`, `EmployeeSortTests.choosingAndClearingASortReordersTheList` |
| AC-03 | Dada a lista carregada, quando o gerente escolhe "Ordem alfabética (A-Z)", então os nomes seguem a ordem pt-BR, sem diferenciar maiúsculas. | `TeamSortTests.alphabeticalTreatsAccentedNamesAsPortuguese`, `TeamSortTests.alphabeticalIgnoresCase` |
| AC-04 | Dada uma opção selecionada, então o campo mostra o nome dela e o "X" no lugar da seta. | `EmployeeSortTests.choosingAndClearingASortReordersTheList` |
| AC-05 | Dada uma opção selecionada, quando o gerente toca no "X", então a lista volta à ordem do Core e o campo volta a "Filtrar por data" com a seta. | `EmployeeSortTests.choosingAndClearingASortReordersTheList` |
| AC-06 | Dado um texto de busca e uma ordenação, então apenas os nomes correspondentes aparecem, na ordem escolhida. | `TeamSortTests.nameSearchAppliesTogetherWithSort` |
| AC-07 | Dada uma opção selecionada, quando a Activity é recriada, então a seleção e a ordem continuam aplicadas. | `EmployeeSortTests.selectedSortSurvivesActivityRecreation` |
| AC-08 | Dado um `id` não numérico, então esse funcionário fica por último nas duas direções, sem erro. | `TeamSortTests.idsThatAreNotNumericGoLastInBothDirections` |

## Technical impact

`TeamViewModel.kt`, `EmployeeFragment.kt`, `fragment_employee.xml`, `strings.xml` e o novo `ic_close.xml`. Sem migração de dados nem mudança de contrato.

**Limitação conhecida:** o `id` indica quando a conta foi criada, não a data de início do funcionário na loja. Um funcionário transferido ou reativado mantém o `id` original, e uma conta inserida com `id` explícito pode ficar fora da ordem.

## Decisions and open questions

| Item | Status | Source or decision owner |
| --- | --- | --- |
| Usar o `id` como indicador da ordem de cadastro | Decidido pelo agente; aguarda confirmação do solicitante | Core não expõe `created_at` em `UserDTO` e não pode mudar |
| Substituir a seta pelo "X" enquanto houver seleção | Decidido pelo agente; aguarda confirmação do solicitante | Pedido do solicitante, sem definição visual |
| Manter o texto "Filtrar por data", embora exista uma opção alfabética | Open | Solicitante |

## Implementation outcome

- **AC-01 a AC-08:** cobertos pelos testes da tabela. `TeamSortTests` passou em `testDebugUnitTest`; `EmployeeSortTests` e `LightModeContrastTests` passaram em `connectedDebugAndroidTest`, filtrado para essas classes, no aparelho físico SM-A546E (Android 16).
- **Encontrado pelo teste:** a margem negativa do "X" fazia o `LinearLayout` recortá-lo na área de padding; corrigido com `clipToPadding="false"` no campo.
- **Pendente:** conferência visual do "X" no aparelho, teste com dados reais do Core e suíte instrumentada completa com o JaCoCo combinado.
