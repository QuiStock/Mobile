# Alertas prioritários da homepage

**Status:** Ready for implementation — por orientação do solicitante; pendências técnicas explicitadas abaixo  
**Issue:** none — vínculo específico a confirmar; QUIS-81 trata notificações e QUIS-126 trata Big Numbers, sem estabelecer o escopo desta seção  
**Product decision owner:** solicitante; responsáveis por Produto e API Core a confirmar

## Problema e objetivo

A Home contém a seção “Alertas prioritários”, mas ainda não apresenta dados reais. Precisamos definir quais situações exigem atenção do usuário, como priorizá-las e quais ações oferecer, antes de implementar a integração com a API Core.

Fonte: solicitação de 21/10/2026 para analisar o XML e a documentação local do DS_Backend e registrar uma spec aberta para refinamento conjunto. A existência de campos e textos no layout é evidência de design, não aprovação das regras de negócio.

## Escopo

**Incluído neste rascunho:** inventário do layout e do contrato disponível; decisões pendentes; critérios candidatos e evidências planejadas para a futura implementação.

**Fora do escopo desta solicitação:** implementar código, alterar backend, executar operações de negócio, criar ou atualizar tickets no Jira. Notificações push, Big Numbers e implementação do chatbot não fazem parte automaticamente desta seção.

## Fontes e fatos verificados

- `app/src/main/res/layout/fragment_home.xml`: título “Alertas prioritários” e RecyclerView `rvAlertas`, com preview de `item_alerta`.
- `app/src/main/res/layout/item_alerta.xml`: nome do produto, categoria/quantidade, data com rótulo “Validade”, botão “Criar promoção de 15%” e botão “Ajuda do FEFO”. São textos fixos no XML.
- `app/src/main/java/com/quistock/quistock/presentation/home/HomeFragment.kt`: não configura adapter nem carrega os alertas; atualmente configura a navegação para Perfil.
- `../DS_Backend/api-contract.md`: versão declarada 1.5.0, atualizada em 07/10/2026; seções Products, Flows, Actions, Chat prototype e Dashboard. Esta análise é documental/local; não comprova o contrato do ambiente implantado.

### Dados e operações documentados

| Fonte | Informação disponível | Limitação relevante |
| --- | --- | --- |
| `GET /products` | `id`, `name`, `category`, `current_stock`, `expiration_days`, `branch`, entre outros campos | Produto consolidado por produto e filial; quantidade decimal. Não entrega data exata de validade, lote ou unidade de medida. Filtro `branch` disponível; correspondência com a loja do perfil precisa ser confirmada. |
| `GET /flows` | `id`, `product_id`, `product_name`, `flow_type`, `status`, `reason` | Filtros por tipo, produto e status; o exemplo de listagem não inclui data da análise. Não há regra documentada de seleção da análise mais recente nem filtro de loja nesta rota. |
| `GET /actions` | `id`, `flow_id`, `product_name`, `action_type`, `status`, `justification` e datas da promoção | Associação ao produto passa pelo fluxo; filtros por status, tipo e fluxo. Não documenta percentual de desconto, prioridade ou filtro de loja. |
| `GET /dashboard/summary` | Contadores, incluindo produtos próximos do vencimento e ações ativas | Não fornece os itens dos cards. O limite padrão de 30 dias pertence ao contador de vencimentos; não define automaticamente a regra dos alertas. |
| `POST /actions/generate` | Gera ações a partir de um fluxo | Para `LOW`, exige início/fim da promoção. Não documenta desconto de 15%. É uma mutação, não uma consulta para renderizar a Home. |
| `PATCH /actions/{id}/status` | Atualiza situação da ação | O papel do botão da Home nesse processo e as permissões precisam ser definidos. |
| `POST /chat` | Protótipo baseado em regras | O contrato orienta o mobile a ainda não depender desta rota; não estabelece uma integração utilizável para “Ajuda do FEFO”. |

`LOW` representa possível excesso ou vencimento e gera `PROMOTION`. `HIGH` representa risco de ruptura e gera `ORDER`. `MEDIUM` indica estoque adequado e não gera sugestão. **Esses tipos descrevem fluxo; não são níveis de prioridade dos alertas.**

`expiration_days` usa a validade mais próxima entre lotes com quantidade positiva, pode ser negativo para vencidos e pode ser nulo quando não há lote com estoque positivo. `current_stock` é consolidado e não equivale à quantidade do lote próximo do vencimento.

## Comportamento esperado

### Regras consolidadas para implementação

Esta seção e a tabela de decisões representam o estado atual aprovado. Rodadas abaixo preservam o histórico; decisões posteriores substituem as anteriores quando indicado.

1. Consultar somente dados da loja do perfil. Cada ID identifica um produto/lote distinto, conforme confirmação do solicitante. Sem análise vigente, omitir; o Core deve fornecer uma única análise vigente por produto.
2. Excluir validade negativa. Com análise HIGH, MEDIUM ou LOW, validade entre 0 e 7 dias inclusive gera alerta de vencimento. Caso contrário, HIGH gera ruptura, LOW gera excesso e MEDIUM não gera alerta.
3. Um card por ID, com ação do risco prioritário. Ordenar vencimento antes de ruptura e excesso; dentro de vencimento, menor prazo primeiro; nos demais, nome. Mostrar todos com rolagem.
4. Nome em até duas linhas com reticências e texto completo acessível; nome, categoria, ID e estoque são obrigatórios. Estoque em unidades inteiras do produto; fração efetiva invalida o item, sem arredondamento. Validade: “Vence hoje”, “Vence em X dias” ou “Não informada”.
5. Ambos os cargos veem os mesmos alertas. “Criar promoção” abre promoção para vencimento/excesso; “Criar pedido” abre pedido para ruptura. Levar o produto selecionado e respeitar permissões do destino, sem mutação ao navegar. Promoção/pedido existente não oculta o alerta; o destino deve evitar duplicações. Percentual e período pertencem ao fluxo de promoção.
6. Manter “Ajuda do FEFO”; ao toque, mensagem breve “Ajuda do FEFO indisponível no momento”, sem chamar chat.
7. Recarregar ao entrar na Home e retornar de promoção/pedido; não por rotação ou simples retorno do background. Mostrar carga apenas na seção, sem lista anterior, mantendo o restante da Home disponível. Impedir publicação de respostas de outra sessão/loja.
8. Sucesso sem itens elegíveis: “Nenhum alerta prioritário no momento”. Itens inválidos: omitir, avisar “Alguns alertas não puderam ser carregados” e registrar categoria técnica segura no Logger. Falha da consulta de produtos ou análises, ou todos os itens inválidos: “Não foi possível carregar os alertas” e “Tentar novamente”, sem lista incompleta nem repetição automática de negócio. Manter autenticação pelo cliente Core existente.

### Prontidão e pendências técnicas

Em 21/10/2026, o solicitante determinou considerar a spec Ready for implementation mesmo sem resolver os pontos técnicos. Esse status não comprova contrato disponível nem autoriza inventá-lo. Permanecem pendentes: recorte/autorização e identificadores de loja (O-04), integração dos destinos e prevenção de duplicação (O-07), detalhes de validação/composição (O-10) e contrato da análise vigente (O-11). Podem ser investigados durante a implementação; dependências sem contrato confirmado devem ser reportadas como limitações, sem presumir sucesso. Manter sem issue até identificar ou criar task própria; nenhuma operação Jira foi autorizada nesta rodada.

### Histórico do refinamento

Decisões confirmadas pelo solicitante em 21/10/2026, na primeira rodada de diálogos:

- Incluir vencimento próximo, excesso de estoque e risco de ruptura.
- Não incluir produtos já vencidos; seu tratamento pertence a outro fluxo, fora desta spec. A regra para produto com lote vencido e outros lotes válidos continua em aberto, pois o contrato consolida os lotes.
- Seguir os dados necessários ao XML `item_alerta`: nome do produto, categoria/quantidade, validade e ações. O formato é de card de produto; essa orientação não define ainda a deduplicação nem a reunião de riscos/sugestões do mesmo produto.

Os demais comportamentos continuam pendentes de refinamento. Incluir risco de ruptura exige definir o conteúdo de validade; a ação correspondente foi decidida na terceira rodada abaixo.

Decisões confirmadas na segunda rodada, em 21/10/2026:

- Vencimento próximo: `expiration_days` entre 0 e 7, inclusive. O dia do vencimento entra; valores negativos ficam fora.
- Ordem entre riscos: vencimento próximo, depois risco de ruptura, depois excesso.
- Um card por produto da loja, com a ação correspondente ao risco mais prioritário. Múltiplas análises ou sugestões não devem duplicar o card. A escolha da análise/sugestão vigente e os desempates dentro de cada risco continuam em aberto.

Decisões confirmadas na terceira rodada, em 21/10/2026:

- Para vencimento próximo ou excesso, a ação principal abre o fluxo de promoção com o produto selecionado. Percentual e período serão definidos nesse fluxo; o texto fixo de 15% no card não representa uma regra aprovada.
- Para risco de ruptura, a ação principal abre o fluxo de pedido de reposição com o produto selecionado.
- Essas ações de entrada navegam para os fluxos; não criam, aprovam ou geram ações Core automaticamente ao tocar o card. O comportamento de submissão nos fluxos não é definido por esta decisão.
- “Ajuda do FEFO” permanece visível e, ao toque, apresenta mensagem de recurso indisponível. Não chama `/chat`. Texto e forma de apresentação da mensagem continuam em aberto.

Decisões confirmadas na quarta rodada, em 21/10/2026:

- Validade: para zero dias, “Vence hoje”; para valor positivo, “Vence em X dias”; quando ausente, “Não informada”. Valores negativos ficam sujeitos à exclusão de vencidos e à decisão sobre lotes mistos.
- A quantidade representa `current_stock`, o estoque consolidado em unidades do produto. A unidade é uma convenção de negócio aprovada pelo solicitante, não um campo retornado pelo Core. Exemplo: para Leite 1L, estoque 5 significa 5 unidades do produto Leite 1L; não converter para litros ou presumir outra embalagem. O formato de quantidades fracionárias permanece aberto.
- `HIGH` identifica risco de ruptura; `LOW` identifica risco de excesso para esta seção. Não substituir esses critérios pelas regras dos contadores de estoque do dashboard. Quando houver também vencimento de 0 a 7 dias, esse risco prevalece no card pela prioridade já aprovada. A seleção da análise vigente permanece em O-11.

O fluxo a detalhar após o refinamento é: carregar os dados autorizados da loja, selecionar os itens elegíveis, aplicar a prioridade acordada, renderizar os campos confirmados e encaminhar cada ação para o destino aprovado. Não criar análises ou sugestões automaticamente ao abrir a tela sem decisão explícita em O-07.

Devem ser definidos estados observáveis de carregamento, lista disponível, ausência de alertas, erro e recuperação. Falha de consulta não comprova ausência de alertas. Textos, visibilidade dos botões, acessibilidade e navegação permanecem sujeitos às decisões abaixo.

### Quinta rodada — decisões de 21/10/2026

- Dentro do grupo de vencimento, ordenar pela validade mais próxima; nos grupos de ruptura e excesso, ordenar pelo nome do produto. Mostrar todos os cards elegíveis com rolagem. Desempates finais de mesma validade/nome ficam como detalhe técnico a documentar.
- Regra de negócio informada pelo solicitante: cada lote é tratado como um produto diferente. Não há separação adicional de lotes na experiência; validade negativa significa produto/lote vencido e o exclui desta seção, mesmo com classificação HIGH ou LOW. Essa decisão substitui as dúvidas anteriores sobre lotes mistos.
- Exigir estoque inteiro em unidades do produto. `current_stock` com parte fracionária é dado incompatível; não arredondar nem truncar. Um número como `5.000`, sem fração efetiva, representa cinco unidades e é compatível. A recuperação de dados incompatíveis será definida em O-10. Esta decisão substitui a pendência anterior de formato fracionário.
- **Divergência de contrato em aberto:** o contrato local descreve agrupamento por código de produto e filial, somando lotes, enquanto a regra de negócio acima considera cada lote um produto distinto. Não presumir que o retorno atual já representa essa regra; confirmar a identidade dos produtos/lotes no Core antes da implementação dependente. O contrato também permite estoque fracionário, que a regra do mobile agora rejeita.

## Decisões e perguntas abertas

### Décima primeira rodada — decisões de 21/10/2026

- Ação principal: “Criar promoção” para vencimento/excesso e “Criar pedido” para ruptura. Ação secundária: “Ajuda do FEFO”. Os rótulos não alteram a decisão de apenas navegar ao fluxo ao tocar.
- O Core deve fornecer uma única análise vigente por produto/lote. O contrato técnico para obter essa análise continua aberto; não escolher por ordem de retorno, maior ID ou data presumida. A Home não deve resolver múltiplas análises por uma regra de negócio inventada no cliente.
- Nome do produto em até duas linhas, com reticências e nome completo acessível ao leitor de tela. Ausência de nome, categoria, ID ou estoque invalida o item, sujeitando-o à omissão, aviso parcial e logs já aprovados. Validade ausente continua permitida, com “Não informada”.

### Décima rodada — decisões de 21/10/2026

- **Refinamento que substitui a restrição da oitava rodada:** vencimento entre 0 e 7 dias pode gerar alerta quando existir análise de qualquer tipo, inclusive MEDIUM. Sem análise, omitir. Fora dessa janela, HIGH gera ruptura, LOW gera excesso e MEDIUM não gera alerta. Validade negativa continua excluindo o produto/lote antes das demais classificações.
- Retornar do background sem navegação não recarrega os alertas; manter o estado atual. Continuam válidas as recargas por entrada na Home e retorno de promoção/pedido, sem recarga por rotação.
- Exibir somente produtos da loja vinculada ao perfil. A correspondência entre identificadores e a garantia de autorização/recorte nas consultas permanecem pendências técnicas do Core, sem confirmação de que o ambiente já as implemente.

### Nona rodada — decisões de 21/10/2026

- Na carga inicial e em atualizações, mostrar carregamento restrito à seção; substituir a lista ao concluir, sem bloquear o restante da Home. Não manter a lista anterior visível durante a carga.
- Consulta com falha ou todos os itens retornados inválidos: erro na seção com “Tentar novamente”, sem repetição automática de negócio. Preservar o tratamento de autenticação do cliente Core existente; essa decisão não cria outro mecanismo de sessão.
- Lista carregada sem alertas elegíveis: “Nenhum alerta prioritário no momento”. Itens corretamente excluídos por regra de negócio não são itens inválidos.
- Erro: “Não foi possível carregar os alertas”. Resposta parcialmente válida: manter os itens válidos e mostrar “Alguns alertas não puderam ser carregados”, com registros técnicos seguros conforme sexta rodada.
- Ao tocar “Ajuda do FEFO”, mostrar mensagem breve “Ajuda do FEFO indisponível no momento”, sem diálogo de confirmação nem chamada ao chat.

### Oitava rodada — decisões de 21/10/2026

- Atualizar ao entrar na Home e ao retornar de promoção ou pedido. Simples rotação não dispara recarga. Retorno do background e apresentação de dados durante atualização ainda precisam ser definidos.
- Manter o alerta enquanto o risco existir, mesmo com promoção/pedido em andamento. Status de ação existente não elimina o card. O fluxo de destino deve tratar a ação existente e evitar duplicações; essa integração ainda precisa ser detalhada, sem presumir que já esteja implementada.
- Sem análise HIGH/LOW, omitir o produto, inclusive se vencer entre hoje e sete dias. Não gerar análise automaticamente ao carregar a Home. O tratamento de análise MEDIUM com vencimento próximo e a identificação da análise vigente continuam em aberto.

### Sétima rodada — confirmação de contrato pelo solicitante em 21/10/2026

O solicitante confirmou que a API atual já devolve produtos com IDs diferentes para lotes distintos da mesma loja e que a documentação local de consolidação está desatualizada. Para esta spec, cada ID retornado representa o produto/lote independente; deduplicar pelo ID, nunca pelo nome ou SKU compartilhado. Dois lotes com IDs diferentes podem produzir dois cards, ainda que tenham o mesmo nome. Validade negativa exclui apenas o item correspondente.

Esta confirmação resolve a dúvida de identidade registrada na quinta e sexta rodadas e prevalece sobre a descrição documental local. Não houve consulta ao ambiente para verificá-la nem alteração do DS_Backend. A divergência documental deve permanecer explícita como limitação da fonte; seleção da análise vigente e composição das consultas continuam abertas em O-11.

### Sexta rodada — decisões de 21/10/2026

- Funcionário e gerente visualizam os mesmos alertas e podem abrir promoção e pedido pelos cards, respeitando as permissões próprias de cada fluxo. Isso não concede novas permissões de criação ou aprovação.
- Dados essenciais incompatíveis, incluindo estoque com fração efetiva: omitir os itens inválidos, manter os válidos e avisar que alguns alertas não puderam ser carregados. Registrar a rejeição no Logger existente com categoria técnica segura, sem payloads, dados pessoais, credenciais ou URLs. Texto do aviso e comportamento quando todos os itens forem inválidos continuam em aberto.
- O solicitante pediu esclarecimento sobre a divergência de identidade produto/lote. O-11 permanece Open: a documentação local descreve soma de lotes por produto/filial; a regra de negócio exige cada lote como produto distinto. É necessário confirmar se os IDs atuais já distinguem lotes ou se o contrato precisa evoluir. Nenhuma das duas possibilidades foi confirmada.

Itens Confirmed registram decisões do solicitante. Itens Open abaixo são pendências técnicas preservadas, compatíveis com o status Ready for implementation solicitado; não representam contratos confirmados.

| ID | Decisão a resolver | Status | Responsável / evidência necessária |
| --- | --- | --- | --- |
| O-01 | Incluir vencimento próximo, excesso e risco de ruptura. Excluir produtos já vencidos, tratados em outro fluxo. Critérios quantitativos e casos de lotes mistos dependem de O-03/O-06. | Confirmed | Solicitante, primeira rodada em 21/10/2026 |
| O-02 | Um card por produto da loja, seguindo os campos do XML, com a ação do risco mais prioritário e sem duplicatas por análise/sugestão. Escolha da análise/sugestão vigente em O-11. | Confirmed | Solicitante, segunda rodada em 21/10/2026 |
| O-03 | Janela de 0 a 7 dias inclusive. Ordem: vencimento, ruptura, excesso. Dentro de vencimento: validade mais próxima; nos demais: nome do produto. Mostrar todos com rolagem. | Confirmed | Solicitante, segunda e quinta rodadas |
| O-04 | Exibir somente a loja vinculada ao perfil, conforme aprovado. Como garantir esse recorte, relacionar identificadores e aplicar autorização no Core permanece aberto. Não presumir isolamento pelo filtro de UI ou nome da filial. | Open — regra de negócio confirmada; contrato pendente | Solicitante, décima rodada; Core para contrato/ambiente |
| O-05 | Funcionário e gerente veem os mesmos alertas e abrem promoção/pedido, respeitando permissões de cada fluxo. Ação em andamento não oculta alerta enquanto houver risco. | Confirmed | Solicitante, sexta e oitava rodadas |
| O-06 | Validade em dias com textos aprovados; estoque inteiro em unidades do produto; negativo na validade exclui item. Nome até duas linhas com reticências e texto completo acessível. Nome, categoria, ID e estoque obrigatórios; validade pode estar ausente. Cada ID distingue um produto/lote, conforme solicitante. | Confirmed | Solicitante, quarta, quinta, sétima e décima primeira rodadas |
| O-07 | “Criar promoção” abre promoção para vencimento/excesso; “Criar pedido” abre pedido para ruptura, com produto selecionado e sem mutação ao navegar. Percentual/período definidos no fluxo. Ação existente não oculta card; destino deve evitar duplicações. Detalhes dessa integração e prevenção técnica de navegação repetida ainda precisam ser verificados. | Open — integração pendente | Solicitante, terceira, oitava e décima primeira rodadas; Mobile/Core para integração |
| O-08 | Manter “Ajuda do FEFO” visível; ao toque, mensagem breve “Ajuda do FEFO indisponível no momento”, sem chamada ao chat. | Confirmed | Solicitante, terceira e nona rodadas |
| O-09 | Atualizar ao entrar na Home e retornar de promoção/pedido; não recarregar por rotação nem simples retorno do background. Carga substitui a lista, sem mostrar dados anteriores. Isolamento de respostas por sessão/loja deve respeitar o mecanismo existente e ser verificado. | Confirmed | Solicitante, oitava a décima rodadas; testes de isolamento em AC-06 |
| O-10 | Carregamento local à seção, substituindo a lista ao concluir. Vazio, erro e parcial com textos aprovados na nona rodada. Falha/todos inválidos: erro e tentativa manual, sem repetição automática de negócio. Itens inválidos omitidos e registrados no Logger. Faltam detalhes técnicos de validação e falha de dependências na composição das consultas. | Open — parcialmente definido | Solicitante, sexta/nona rodadas; Mobile/Core para contrato e validação |
| O-11 | Exigir análise existente; qualquer tipo permite vencimento de 0 a 7 dias, inclusive MEDIUM. Fora da janela: HIGH para ruptura, LOW para excesso, MEDIUM sem alerta. Sem geração automática. Core deve fornecer uma única análise vigente por ID de produto/lote; contrato e composição de consultas continuam abertos. IDs distintos por lote confirmados pelo solicitante, sem verificação live. | Open — contrato técnico pendente | Solicitante, quarta a décima primeira rodadas; Core + Mobile |
| O-12 | Manter sem issue até identificar ou criar task específica. Spec Ready for implementation por determinação do solicitante, com pendências técnicas explícitas. | Confirmed | Solicitante, rodada final em 21/10/2026 |

## Critérios de aceitação e evidências planejadas

Critérios baseados nas regras aprovadas; evidências ainda planejadas, sem testes executados. Seguir Red–Green–Refactor na implementação e verificar os contratos técnicos pendentes.

| ID | Dado / quando / então observável | Dependências | Teste ou verificação planejada |
| --- | --- | --- | --- |
| AC-01 | Dado usuário elegível com loja vinculada, ao carregar a seção, exibir somente itens autorizados da loja, conforme elegibilidade e papel acordados. | O-01, O-04, O-05 | Unitários de seleção e estado; integração de autorização Core com duas lojas e papéis relevantes. Filtro local não comprova autorização do servidor. |
| AC-02 | Com análise vigente, dias 0 a 7 geram vencimento inclusive para MEDIUM; dias negativos excluem. Fora da janela, HIGH gera ruptura e LOW excesso; MEDIUM sem alerta. Sem análise, omitir. Um card por ID com risco prioritário; vencimento por prazo, demais por nome; todos com rolagem. | O-01, O-02, O-03, O-11 | Unitários com dias -1, 0, 7, 8 e nulo, HIGH/MEDIUM/LOW e ausência de análise; IDs distintos de mesmo nome; múltiplos riscos. |
| AC-03 | Dado item elegível, renderizar nome, categoria e estoque total em unidades do produto; mostrar “Vence hoje” para zero dias, “Vence em X dias” para positivos e “Não informada” para validade ausente. Formato fracionário e demais casos pendentes em O-06. | O-06 | Unitários de mapeamento/formatação com dias zero/positivos/nulos e estoque inteiro/fracionário; instrumentado de binding; revisão manual de nomes longos, fonte ampliada e leitor de tela, necessária para avaliar legibilidade. |
| AC-04 | Dado card de vencimento/excesso, ao tocar a ação principal, abrir promoção com o produto selecionado; para ruptura, abrir pedido com o produto selecionado. Não executar mutação Core nessa navegação. Ao tocar “Ajuda do FEFO”, mostrar indisponibilidade sem chamar `/chat`. Rótulos, elegibilidade e proteção contra navegação repetida ainda dependem de refinamento. | O-05, O-07, O-08 | Unitários de eventos/ausência de mutações; instrumentado de navegação, contexto do produto e mensagem de indisponibilidade. |
| AC-05 | Dado carregamento, resposta vazia ou falha, apresentar estados distintos e recuperação conforme política aprovada, sem tratar erro como lista vazia. | O-09, O-10, O-11 | Unitários de estado e recuperação para rede/timeout, 401/403, 429, 5xx, corpo inválido e falha parcial; instrumentado dos estados visíveis. |
| AC-06 | Dada atualização ou mudança de sessão/loja, aplicar a política de recarga e impedir publicação de resultados pertencentes ao contexto anterior. | O-04, O-09 | Unitários com respostas atrasadas e mudança de sessão/loja; instrumentado de retorno à Home e recriação conforme política decidida. |

## Impacto técnico

Arquivos provavelmente afetados na futura implementação: `HomeFragment.kt`, `fragment_home.xml`, `item_alerta.xml` e recursos de texto. A integração deverá respeitar `presentation -> domain <- data`, Views/ViewBinding/ViewModels e Koin, usando o cliente Core autenticado existente. Modelos e operações necessários só serão definidos depois das decisões; este rascunho não autoriza novas camadas ou módulos.

Dependência externa: API Core/DS_Backend e seu snapshot ERP. As fontes consultadas são locais; comportamento real, autorização por loja e compatibilidade das respostas continuam sem validação. Não registrar credenciais, URLs privadas, payloads reais ou dados pessoais. Não há migração, flag ou persistência decidida.

## Resultado da implementação

Não implementado. Spec refinada e marcada Ready for implementation a pedido do solicitante, com pendências técnicas abertas. Nenhum teste automatizado ou chamada à API foi executado para esta spec. Testes, verificações manuais e eventuais alterações de contrato serão registrados na entrega futura.
