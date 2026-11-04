# Controle de produtos com dados Core

## Rodada de 04/11/2026 às 15:37

Dos 122 testes, resta neste fluxo uma falha após buscar "leite", não na lista
inicial: o adapter já tem um item, mas a view ainda está em transição. O helper
agora exige layout concluído e ausência de animação/pending updates. A jornada
desabilita o ItemAnimator somente na Activity do teste, pois cobre conteúdo,
filtro e navegação, não animações de remoção/movimentação. As verificações de
visibilidade e inclusão/exclusão continuam obrigatórias. Produção inalterada.

## Rodada de 04/11/2026 às 15:13

O teste de lista ainda falha: `scrollTo` solicita o retângulo ao RecyclerView
interno, sem alcançar o item pelo ScrollView externo. A ação do teste agora
converte o retângulo do nome para coordenadas da página e rola explicitamente
o ScrollView, aguardando o main thread antes de verificar visibilidade/clicar.
As asserções de busca/filtro e destino foram preservadas. Nenhum layout ou
comportamento de produção foi alterado. `git diff --check` passou; Gradle não
iniciou os checks por erro de conexão loopback, sem confirmação instrumentada.

**Status:** In progress
**Issue:** none
**Product decision owner:** solicitante

## Problem and goal

A tela Controle de produtos e o detalhe exibiam dados de exemplo. A entrega deve consultar os produtos da loja da sessão ativa e mostrar o produto selecionado sem completar campos ausentes com valores fictícios.

## Scope

**In scope:** lista real por loja autorizada, busca por nome, filtro de fluxo, loading/vazio/erro com retry, detalhe por seleção real, FEFO na conversa atual do chatbot com nome e categoria.

**Out of scope:** escrita no Core/ERP, criação de conversa, métricas que o contrato atual não fornece e mudanças no Core/Auth.

## Expected behavior

Ao abrir Controle, consultar `PriorityAlertsRepository` usando `storeId` e `storeCode` do perfil Ready da geração de sessão ativa. Exibir somente produtos retornados pelo Core, inclusive produtos sem análise/fluxo. Busca e filtro atuam sobre a lista carregada. Erro, carregamento e resposta válida sem produtos são estados diferentes; erro oferece nova tentativa.

Abrir um item transfere seus dados reais ao detalhe. Se o argumento estiver ausente, malformado ou não corresponder à sessão/lista atual, os campos são apresentados como indisponíveis e o FEFO não navega. Estoque, dias de validade, fluxo e campos de produto presentes no contrato são mostrados como recebidos; validade negativa aparece como vencida há N dias, zero como vence hoje e valores positivos como prazo restante. `sales_7d` é dividido por sete e exibido como média diária, arredondada para três casas com HALF_UP; SKU ausente é indicado como indisponível mantendo a categoria real. Outros valores opcionais ausentes também são apresentados como indisponíveis. Não há data absoluta de validade no contrato atual. FEFO abre a conversa existente no chatbot e fornece nome/categoria no contexto do próximo envio.

## Acceptance criteria and evidence

| ID | Observable given / when / then | Planned test or check |
| --- | --- | --- |
| AC-01 | Sessão ativa com loja vinculada consulta somente a loja do perfil; produtos sem análise também aparecem. | Unitários do ViewModel/filtro e repository existente. |
| AC-02 | Busca por nome e filtro de fluxo refinam a lista real; reset mostra os itens elegíveis. | Unitário de filtro. |
| AC-03 | Carga, vazio válido e falha são visivelmente distintos; retry relê a loja atual. | Unitário de estado/retry e check manual de UI. |
| AC-04 | Detalhe apresenta apenas valores presentes no produto selecionado; falta de produto/valor não inventa dados. | Unitários de navegação e verificação manual. |
| AC-05 | FEFO navega para o chatbot atual e fornece nome/categoria no contexto sem substituir a conversa. | Unitário do contexto de produto e check manual de navegação. |
| AC-06 | Controle e detalhe resolvem o ViewModel compartilhado da Activity pelo módulo Koin de produção, sem encerrar o processo. | `AppModulesTest.product control view model resolves from the presentation module` e `ProductControlFragmentTests`. |

## Technical impact

`ProductControlFragment`, `ProductDetailFragment`, novo ViewModel/adapter e respectivos layouts; registro Koin. A consulta usa o repositório Core existente e as barreiras de sessão/perfil. Nenhuma chamada de escrita é introduzida.

## Decisions and open questions

| Item | Status | Source or decision owner |
| --- | --- | --- |
| Contrato expõe estoque, dias de validade e fluxo; vendas semanais `sales_7d` e SKU também podem estar presentes. Não há data absoluta de validade. | Confirmado; calcular média diária como `sales_7d / 7`, arredondar HALF_UP para três casas e preservar categoria se SKU ausente. | `PriorityAlertsRepository` e contrato DTO Core |

## Implementation outcome

Preencher no PR com arquivos entregues e checks realmente executados.

### Correção de instrumentação — 04/11/2026

O relatório local registra encerramento do processo em
`ProductControlFragmentTests.listSearchAndFlowFilterOpenTheSelectedProductDetail`:
`NoDefinitionFoundException` para `ProductControlViewModel`. O módulo de apresentação
importava a classe, mas não registrava sua factory. Adicionado `viewModelOf` no módulo
de produção e teste de regressão que resolve a classe com suas dependências e limpa
seu `ViewModelStore`. Nenhuma regra de produto ou contrato foi alterado.

Tentativas de executar o teste de regressão foram bloqueadas primeiro por acesso ao
cache Gradle e, com execução ampliada, por `Unable to establish loopback connection`.
Não houve execução dos testes nem confirmação de aprovação após a correção.

Na rodada seguinte, o relatório registra 98 testes e cinco falhas, sem repetir a
ausência do registro Koin. A falha de AC-02 ocorre ao exigir visibilidade de
`Leite integral` fora do viewport. O teste agora rola até cada item e aguarda a
aplicação da lista do adapter depois de carregar, buscar e filtrar. Preservadas as
asserções de inclusão/exclusão e a navegação para o detalhe. `git diff --check`
passou; a nova execução Gradle (regressão de DI, compilação androidTest e Spotless)
não iniciou os checks por `Unable to establish loopback connection`.
