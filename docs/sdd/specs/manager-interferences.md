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
| Dynamic listing data source | Confirmed | `GET /actions`; see [manager-interference-decisions.md](manager-interference-decisions.md) |
| Action navigation when clicking "Analisar" | Confirmed | Opens the review screen for the type; see [manager-interference-decisions.md](manager-interference-decisions.md) |

## Implementation outcome

Added the `ManagerInterferencesFragment` shell, `fragment_manager_interferences.xml` layout, and registered routes in `nav_graph.xml` and `NavGraph.kt`. Fragment business logic is deferred to teammate as requested.


## Correção de cores do hub (02/11/2026)

Solicitação: adequar a tela gerencial de Interferências ao tema claro conforme imagem
fornecida, preservando formato, dimensões, espaçamentos e navegação.

- Fundo, títulos, subtítulos e cartões usam os tokens existentes de cada tema.
- Filtros selecionados mantêm violeta, com texto e ícone brancos; filtros sem seleção
  usam texto principal e ícone de destaque do tema.
- Produto e resumo ficam legíveis nos cartões; Analisar mantém texto branco sobre violeta.
- Estados de carregamento, vazio e erro continuam usando texto adaptável ao tema.
- Contratos, regras de decisão, detalhes e disposição da tela ficam fora desta correção.

| ID | Observable given / when / then | Planned test or check |
| --- | --- | --- |
| AC-04 | No tema claro, fundo claro e textos escuros aparecem no cabeçalho, seções e cartões. | Inspeção dos recursos e verificação manual no app; contraste é visual. |
| AC-05 | Ao selecionar e limpar cada filtro, texto e ícone permanecem legíveis em ambos os temas. | Inspeção do seletor e propagação de estado; verificação manual nos dois temas. |
| AC-06 | A correção preserva dimensões, espaçamentos, hierarquia e navegação. | Comparação do diff XML com a versão anterior. |

Verificação visual em dispositivo pendente. Não há novas regras de negócio ou contratos.

Evidência desta correção: XML validado e comparação automatizada com HEAD confirmou
hierarquia, dimensões, margens, paddings e tamanhos de texto preservados nos dois layouts.
`git diff --check` sem erros. Tentativas de `testDebugUnitTest spotlessCheck detekt lintDebug
assembleRelease` ficaram sem saída e foram interrompidas; nenhum desses checks está
confirmado nesta rodada. AC-04/05 ainda requerem conferência visual no aparelho.
