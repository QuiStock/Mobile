# Controle de produtos com dados Core

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

## Technical impact

`ProductControlFragment`, `ProductDetailFragment`, novo ViewModel/adapter e respectivos layouts; registro Koin. A consulta usa o repositório Core existente e as barreiras de sessão/perfil. Nenhuma chamada de escrita é introduzida.

## Decisions and open questions

| Item | Status | Source or decision owner |
| --- | --- | --- |
| Contrato expõe estoque, dias de validade e fluxo; vendas semanais `sales_7d` e SKU também podem estar presentes. Não há data absoluta de validade. | Confirmado; calcular média diária como `sales_7d / 7`, arredondar HALF_UP para três casas e preservar categoria se SKU ausente. | `PriorityAlertsRepository` e contrato DTO Core |

## Implementation outcome

Preencher no PR com arquivos entregues e checks realmente executados.
