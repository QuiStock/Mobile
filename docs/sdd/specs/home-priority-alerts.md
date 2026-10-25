# Alertas prioritários da homepage

**Status:** In progress — correção mobile e avisos implementados; testes/validações delegados ao solicitante; submissão/aprovação Core real fora desta correção  
**Issue:** none — vínculo específico a confirmar; QUIS-81 trata notificações e QUIS-126 trata Big Numbers, sem estabelecer o escopo desta seção  
**Product decision owner:** solicitante; responsáveis por Produto e API Core a confirmar

## Revisão de prontidão — estado normativo de 25/10/2026

Esta seção e os critérios RC abaixo consolidam as decisões mais recentes e prevalecem sobre textos conflitantes nas rodadas e tabelas históricas deste arquivo. A revisão definiu o escopo Ready para correção, posteriormente implementado conforme o resultado abaixo. A existência de código e testes escritos não comprova aprovação dos checks; nenhuma validação foi executada pelo agente nesta entrega.

### Escopo pronto para correção

1. **Identidade e loja:** um card por `codigo_produto_erp:codigo_filial_erp`, consolidando lotes. Consultar somente a loja do perfil; contratos de `store_id` e autorização Core confirmados pelo responsável via solicitante. Não agrupar itens distintos pelo nome. Rotas por `store_id` podem substituir resolução por nome e consultas por produto, preservando o recorte da sessão.
2. **Quantidade:** aceitar decimais com até três casas efetivas, sem conversão para inteiro, usando representação decimal exata em domínio/transporte/navegação. Exibir categoria e quantidade sem sufixo de unidade, sem zeros fracionários desnecessários. A referência local Core documenta `NUMERIC(14,3)`; fração além dessa precisão é incompatível, sem truncamento ou arredondamento silencioso. A unidade futura não bloqueia esta entrega.
3. **Validade:** usar exclusivamente `expiration_days` de `/products`, sem recalcular pela data da análise ou pelo relógio mobile. Zero: “Vence hoje”; um: “Vence em 1 dia”; demais positivos: “Vence em X dias”; nulo: “Não informada”. Excluir todo o produto com valor negativo. A ocultação de possíveis lotes válidos consolidados foi aceita temporariamente pelo solicitante. Fixar o fuso de negócio permanece melhoria Core e não bloqueia o consumo do valor retornado no MVP.
4. **Histórico como apoio à decisão:** o solicitante aprovou tolerar possível desatualização, com checagem humana no ERP e sem execução de operações ERP pelo app. Substituir o requisito de análise comprovadamente vigente pelo uso de análise histórica mais recente com data confiável. Nunca rotular como “vigente”, “atualizada” ou sincronizada com o estoque atual. Não gerar análises automaticamente.
5. **Base técnica da seleção temporal:** no código local consultado, `FlowDTO.analysisDate` é exposto como `analysis_date` do tipo Instant; `FlowRepository` obtém `pa.created_at` e o inclui na listagem. O exemplo documental de análise também contém timestamp com offset. Isso permite implementar seleção pelo maior instante ISO-8601 com offset/UTC para cada produto, independentemente da ordem da resposta e sem usar o maior ID como substituto de data. Compatibilidade do ambiente continua sem execução de smoke test.
6. **Falhas na seleção temporal:** os registros usados para estabelecer a análise mais recente precisam de ID, produto, tipo/status reconhecidos e data válida. Sem histórico, omitir por ausência de análise. Se uma data ausente/inválida impedir determinar qual análise é a mais recente, ou houver empate entre análises distintas no maior instante, tratar o produto como incompatível: omissão com aviso parcial/log, ou erro se todos forem inválidos. Não recuar silenciosamente para uma análise mais antiga ou usar ordem/ID como desempate. Essas salvaguardas aplicam a política de dados incompatíveis já aprovada; não comprovam a atualidade da análise escolhida.
7. **Riscos e ordem:** com análise selecionada HIGH/MEDIUM/LOW, validade de 0 a 7 dias gera vencimento; fora da janela, HIGH gera ruptura, LOW gera “Risco de excesso ou vencimento” e MEDIUM não gera alerta. Um card com risco prioritário: vencimento, ruptura, excesso/vencimento; dentro do primeiro grupo, menor prazo; demais, nome. Mostrar todos com rolagem. Desempates visuais finais por ID não selecionam análise histórica.
8. **Ações do card:** “Criar pedido” abre o destino para ruptura. “Criar promoção” abre o destino para vencimento/LOW, mas é oculto quando a análise selecionada for MEDIUM. MEDIUM com validade próxima mantém card e “Ajuda do FEFO”. Não converter HIGH ou MEDIUM em LOW para forçar geração. Navegar não gera/aprova ação. Promoções/pedidos existentes não ocultam o alerta. FEFO mantém mensagem breve de indisponibilidade.
9. **Avisos somente para gerente autenticado:** usar exclusivamente `ProfileState.Ready` da geração de sessão atual com `role == UserRole.MANAGER`. Não inferir pelo layout/destino, preferência local ou argumento de navegação. EMPLOYEE não vê os avisos de desatualização/conferência, timestamp gerencial da análise ou confirmação de checagem descritos abaixo. Sem perfil válido, não mostrar essas informações. Troca/saída de sessão remove informações do gerente anterior.
10. **Home do gerente:** aviso persistente e discreto: “As análises podem não refletir alterações recentes. Confira os dados no ERP antes de decidir.” Não repetir diálogo a cada carga. Manter alertas de erro/resultado parcial para todos os cargos; eles não são os avisos gerenciais restritos.
11. **Detalhes de solicitação para gerente:** próximo aos dados/ação de aprovação, mostrar “Confira no ERP o estoque, a validade e as condições desta solicitação. A análise pode estar desatualizada.” Quando houver a data da análise realmente associada à solicitação, exibir “Análise realizada em…” com fuso identificável; não substituir por data da consulta nem pela análise mais recente de outro fluxo. Sem essa associação/data, omitir o timestamp. Esses elementos informativos podem ser implementados sem habilitar envio/aprovação Core.
12. **Confirmação de checagem do gerente:** antes de qualquer aprovação efetivamente implementada, mostrar “Confirme que você conferiu os dados atuais no ERP e revisou esta solicitação.” Ações “Voltar e conferir” (não prossegue) e “Conferi e aprovar” (prossegue uma vez no fluxo de aprovação existente). Complemento: “Esta aprovação registra a decisão no QuiStock. Nenhuma operação será executada no ERP.” Não apresentar sucesso/afirmação de registro se o fluxo real ainda não existe. A implementação inicial dos detalhes gerenciais contém somente layouts; criar aprovação remota não está autorizado por esta correção documental.
13. **Limites da checagem:** confirmação é declaração do gerente, não verificação automatizada do ERP. Não declarar auditoria persistida sem suporte contratual. Não criar um endpoint ou payload de auditoria por dedução. O backend ainda permite funcionário aprovar/rejeitar, segundo o responsável; avisos e ocultação mobile não corrigem autorização do servidor.
14. **Estados e ciclo de vida preservados:** carga somente na seção, retry manual, erro total em falha de produtos/análises, aviso parcial para itens incompatíveis, vazio apenas após resposta válida sem elegíveis. Recarregar por entrada na Home/retorno de promoção ou pedido, não por rotação/background. Preservar nome completo acessível, duas linhas com reticências, proteção contra toques repetidos e descarte de respostas de outra sessão/loja.

### Prontidão e limites

**Pronto:** correção dos modelos/parsers/argumentos para decimais; identidade consolidada; seleção temporal defensiva; copy LOW; MEDIUM sem promoção; avisos exclusivos para MANAGER e mensagens informativas dos detalhes onde houver dados reais. Não é necessária uma rota de vigência Core para esse MVP consultivo.

**Não incluído/pronto para entrega completa:** submissão de promoção/pedido, associação de uma solicitação real aos detalhes gerenciais, aprovação remota, prevenção de duplicação entre análises e persistência de auditoria. Seus contratos/fluxos continuam pendentes. A confirmação de checagem é requisito pronto para integrar ao fluxo real de aprovação, mas não autoriza criar ou simular esse fluxo. Mantêm-se explícitas as limitações Core de desconto/quantidade/data do pedido, fuso e permissão de funcionário.

### Critérios de aceitação da correção e evidência planejada

| ID | Resultado observável | Evidência planejada, não executada |
| --- | --- | --- |
| RC-01 | Estoques 5, 5.000 e 2.375 são aceitos como decimais exatos; exibir 5 e 2,375 na localização portuguesa, sem unidade; precisão efetiva acima de três casas é incompatível. | Unitários de parser, domínio, formatação e round-trip dos argumentos de navegação; adaptar os testes antigos que exigem rejeição de frações. |
| RC-02 | Produto consolidado com validade negativa fica fora, mesmo HIGH/LOW; validade nula e limites 0/7/8 seguem regra; LOW usa o novo rótulo. | Unitários de seleção e verificação manual dos textos. |
| RC-03 | Histórico desordenado seleciona o maior instante, inclusive com offsets diferentes; não usa ID/ordem. Sem data confiável ou empate temporal ambíguo, não escolhe arbitrariamente; aplicar parcial/erro e logs sem payload. | Unitários de seleção temporal, histórico vazio, único registro antigo, datas inválidas/ausentes e empate. Registro antigo aceito não é chamado de vigente. |
| RC-04 | MEDIUM próximo do vencimento tem card e FEFO, sem promoção; demais ações preservam produto selecionado e não mutam Core ao navegar. | Unitários de modelo de apresentação; instrumentado/manual de binding e navegação. |
| RC-05 | MANAGER da sessão atual vê avisos de Home/detalhes; EMPLOYEE e perfil ausente não os veem. Datas gerenciais só aparecem quando ligadas à análise correta; troca de sessão remove conteúdo anterior. | Unitários parametrizados de cargo/geração e instrumentado/manual dos destinos, rotação e troca de sessão. |
| RC-06 | No fluxo real de aprovação quando disponível, cancelar a checagem não aprova; confirmar prossegue uma vez, sem operação ERP; falha remota não mostra sucesso. Sem esse fluxo, não simular decisão registrada. | Instrumentado de confirmação/cancelamento/toques repetidos; integração da aprovação permanece dependente de entrega própria. |
| RC-07 | Dados de outra loja e campos essenciais incompatíveis continuam rejeitados; erro não vira vazio; isolamento de sessão e lifecycle mantidos. | Adaptar testes de repositório/ViewModel; confirmação live da autorização Core fica pendente. |

Não executar testes, builds ou validações nesta revisão. Em futura implementação, seguir a orientação de execução vigente do solicitante; testes escritos ou planejados não representam checks aprovados.

## Resultado da correção mobile — 25/10/2026

Solicitante autorizou implementar em commits semânticos, sem executar validações ou tasks Gradle. Implementação entregue para verificação pelo solicitante. **Não foram executados testes unitários/instrumentados, detekt, build, formatter, lint, preview ou chamadas à API real.** A etapa Red–Green executável foi delegada ao solicitante por sua instrução explícita.

- RC-01: estoque usa BigDecimal no domínio e parser, com precisão efetiva até três casas; navegação preserva representação decimal por string e metadados da análise. Formatação localizada sem unidade nem zeros desnecessários. Dados fora da precisão não são arredondados.
- RC-02/03: LOW tem o texto aprovado. `HistoricalAnalysisMapper` seleciona por maior instante com offset/UTC, valida cada registro relevante e rejeita data ausente/inválida, conflito de identidade ou empate temporal entre análises distintas. Duplicatas idênticas não viram análises distintas. Não se afirma vigência. Validade permanece a de produtos; exclusão de validade negativa preservada. Mantidas as rotas já existentes: branches, produtos por filial e análises por produto, sem mudança Core.
- RC-04: card MEDIUM mantém FEFO, oculta promoção e remove seu acionamento; binding de item reciclado restaura o botão para outros tipos. A navegação também verifica elegibilidade da ação. Destinos preservam produto/estoque/análise, sem executar mutação ao navegar.
- RC-05: avisos na Home e nos detalhes de pedido/promoção são slots do binding compartilhado de perfil, inicialmente ocultos e sem persistência de estado da View. `ManagerReviewPolicy` exige perfil Ready da geração atual, sessão ativa e cargo MANAGER. EMPLOYEE e perfil ausente não exibem esses slots. Não foi exibido timestamp nos detalhes gerenciais porque essas telas ainda não têm solicitação real associada; não se reutilizou uma análise arbitrária para preencher data.
- RC-06: continua condicional à entrega de aprovação real. Os detalhes gerenciais ainda não integram aprovação remota; não foi criado diálogo que simule decisão registrada nem auditoria fictícia. Os textos/requisito de confirmação permanecem especificados para integração futura com esse fluxo. Esta entrega implementa a orientação informativa, não habilita operações Core ou ERP.
- RC-07: mantidas validação de loja, política parcial/erro/vazio, cancelamento e publicação protegida por sessão. O binding remove os avisos quando o perfil/sessão deixam de satisfazer a política. Autorização Core foi informada pelo responsável, sem validação live pelo mobile.

Testes adicionados/adaptados, **sem execução**:

- `RetrofitPriorityAlertsRepositoryTests`, `HistoricalAnalysisMapperTests`, `LoadPriorityAlertsUseCaseTests`, `AlertProductPresentationTests`, `ManagerReviewPolicyTests` e fixtures de `HomeViewModelTests`: frações, precisão, data/offset, ambiguidades, riscos e política de gerente por cargo/geração.
- Instrumentados `AlertProductBindingTests`, `ManagerReviewNoticeTests` e `EmployeeReviewNoticeTests`: round-trip decimal/metadados, reciclagem MEDIUM/LOW, aviso em recriação e ocultação mesmo ao abrir layout gerencial com funcionário. A fixture autenticada isola alertas em repositório falso e admite cargo configurável, sem acesso Core para esses testes.

Permanecem fora do escopo: envio/aprovação remotos, auditoria persistida, associação de solicitações reais aos detalhes e correção de permissões no backend. Essas dependências não foram apresentadas como implementadas. Fuso de negócio Core continua não fixado; mobile consome os dias retornados sem recalcular validade.

## Registro anterior de refinamento e implementação

As seções abaixo preservam o histórico e o que foi implementado antes desta revisão. Requisitos conflitantes — análise obrigatoriamente vigente, rejeição de frações ou ID por lote — não prevalecem sobre a seção normativa acima.

## Esclarecimentos do responsável pela Core — 25/10/2026

Fonte: respostas ao questionário encaminhadas pelo solicitante nesta data, sem consulta ao ambiente pela implementação mobile. Estas confirmações substituem premissas conflitantes nas rodadas históricas abaixo. Não constituem aprovação automática de mudanças nas regras de negócio, nem evidência de código mobile adaptado.

### Contrato esclarecido

- **Produto consolidado por loja, não por lote:** `/products` agrupa lotes do mesmo produto na mesma loja. ID estável `codigo_produto_erp:codigo_filial_erp` enquanto os códigos não mudarem. Somente códigos de produto ERP distintos produzem itens distintos para lotes. `current_stock` soma lotes ativos; `expiration_days` usa a menor validade entre lotes com saldo positivo. A confirmação anterior de um ID por lote estava incorreta para o caso geral.
- Estoque pode ser fracionário; não é negativo nem nulo; sem saldo retorna zero. Validade nula quando não há lote ativo com saldo positivo e validade disponível; zero é hoje e negativo é vencido. Hoje é calculado no fuso da JVM, com importação de datas em UTC; **fuso de negócio ainda não fixado**.
- Vínculos: `/profile.store_id = /branches.store_id` (SQL); `/profile.store_code = /branches.id` (ERP); `/profile.store_name = /branches.name = /products.branch`. `branch` aceita nome ou código ERP; `store_id` aceita ID interno. Funcionário e gerente consultam `/branches` e recebem sua loja. Core restringe produtos/análises ao escopo autorizado mesmo sem filtros. `store_id` sem permissão: 403; produto por ID fora do escopo: 404; filtro sem correspondência: lista vazia. Contrato confirmado pelo responsável, sem smoke test mobile.
- `/flows?product_id=...` retorna **histórico**; `product_id` é o ID consolidado de `/products`. Não há rota com garantia de análise vigente. Mudança de estoque não invalida análises automaticamente; não há versão para comparação. Até uma única análise pode estar antiga: a quantidade de registros não comprova vigência. Lista vazia pode significar ausência ou filtros/IDs incorretos. Tipos HIGH/MEDIUM/LOW e status ANALYZED mantidos.
- Para reduzir chamadas: `/products?store_id=...` e `/flows?store_id=...`. Não há endpoint único nem paginação; respostas podem refletir momentos diferentes, sem versão compartilhada. Essa possibilidade ainda não foi adotada no código mobile.
- `/actions?flow_id=...` consulta ações; associação ao produto via fluxo. Ativos: GENERATED, IN_EMPLOYEE_TRIAGE, SENT_TO_MANAGER e APPROVED. Aprovar não executa no ERP. `/actions/generate` usa `flow_id`: HIGH gera pedido, LOW gera promoção com `promotion_valid_from` e `promotion_valid_until`, MEDIUM não gera ação. Desconto, quantidade e data desejada do pedido não são aceitos nessa request.
- Repetir o mesmo `flow_id` devolve a ação existente com 201; outra análise do mesmo produto pode gerar outra ação. Portanto, reutilização por fluxo não impede duplicação por produto entre análises.
- **Permissão atual a corrigir no backend:** funcionário e gerente podem alterar status, inclusive aprovar/rejeitar, dentro da loja autorizada. Isso descreve a implementação atual e não aprova essa permissão como regra de produto.
- Para os cards, validade deve vir de `/products`, pois a análise pode conter validade antiga. LOW também pode representar risco de vencimento além dos sete dias; o responsável sugere “risco de excesso ou vencimento”. Ação em andamento não impede manter o alerta.

### Decisões após os esclarecimentos — 25/10/2026

- **R-01 parcialmente resolvido:** aceitar estoque fracionário e preservar até três casas decimais, sem arredondar para inteiro. A unidade exibida deve corresponder ao produto. Falta confirmar a fonte/contrato da unidade de medida; não deduzir litros, quilos ou embalagens a partir do nome. O mobile entregue ainda usa Long e rejeita frações; adaptação de modelo, parser, navegação, formatação e testes permanece pendente. Política para precisão superior a três casas ainda não definida.
- **R-02 resolvido para esta entrega:** manter a exclusão do produto inteiro quando `expiration_days` for negativo, mesmo que outros lotes tenham saldo válido. O solicitante aceita temporariamente que alertas do saldo válido possam ficar ocultos pela consolidação; isso não demonstra ausência de risco nesses lotes nem autoriza promoções de saldo vencido.
- **R-04 resolvido:** adotar “Risco de excesso ou vencimento” para LOW fora da janela de sete dias e manter a ação de abrir promoção. Alteração de copy no código ainda pendente.
- As decisões acima substituem estoque inteiro e rótulo exclusivo de excesso nas regras anteriores. Não houve mudança de código nesta rodada; continuam abertas a garantia de vigência, unidade de medida, fuso e compatibilidade da ação de promoção com análise MEDIUM.

### Decisões reabertas e efeitos no mobile

Refinamento adicional do solicitante em 25/10/2026:

- Até existir unidade contratual confirmada, exibir somente categoria e quantidade numérica, sem sufixo de unidade. Não bloquear a apresentação por ausência de unidade, nem inferir unidade pelo nome do produto. Isso resolve a apresentação temporária de R-01; precisão superior a três casas permanece sem regra.
- Em alerta de vencimento com análise MEDIUM, manter o card e “Ajuda do FEFO”, mas ocultar “Criar promoção”. Não gerar promoção para MEDIUM nem substituir o tipo da análise no mobile. A incompatibilidade dessa ação em R-06 está resolvida para a Home; submissão e permissões dos outros fluxos continuam pendentes.
- Para R-03, o solicitante inicialmente pediu esclarecimento. Posteriormente aprovou tolerância à desatualização e checagem humana com avisos somente para MANAGER; a revisão normativa acima substitui a exigência de vigência neste MVP. O contrato Core continua histórico e não passou a garantir atualidade.

| ID | Ponto ainda Open | Efeito da resposta |
| --- | --- | --- |
| R-01 | Aceitar frações até três casas, sem arredondar para inteiro: Confirmed. Fonte da unidade e precisão superior: Open. | Decisão do solicitante em 25/10; código ainda rejeita frações e requer adaptação. Não inventar unidade pelo nome. |
| R-02 | Manter exclusão do produto inteiro com validade negativa: Confirmed, temporariamente. | Solicitante aceita em 25/10 que saldo válido de outros lotes pode ficar sem alerta; Core não separa esse saldo no retorno. |
| R-03 | Como garantir análise vigente e compatibilidade com estoque atual? | Depende de evolução/definição Core. Não adotar última análise, maior ID, primeiro registro ou registro único como prova de vigência sem acordo. O mobile atual rejeita múltiplas análises, mas aceita uma única sem garantia real de atualidade. |
| R-04 | Adotar “Risco de excesso ou vencimento” para LOW fora da janela de sete dias e manter abertura de promoção: Confirmed. | Solicitante aprovou em 25/10; copy no código ainda pendente. |
| R-05 | Qual é o fuso de negócio? | Core ainda precisa fixá-lo. Não recalcular validade no mobile a partir de outro relógio/fuso. |
| R-06 | Como alinhar fluxos de promoção/pedido e permissões? | Desconto/quantidade/data ainda sem suporte; MEDIUM com alerta de validade não permite gerar promoção pelo contrato atual. Aprovação por funcionário exige correção/regra explícita. Não habilitar mutações por dedução. |

Nesta atualização foram alterados apenas documentos. Código, testes e contratos do DS_Backend permanecem inalterados; sem testes ou validações executados. Estoque inteiro e rótulo de excesso descrevem o mobile entregue, mas foram substituídos nas regras aprovadas por frações e “Risco de excesso ou vencimento”. A exclusão de validade negativa foi reafirmada com sua limitação explícita.

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

1. Consultar somente dados da loja do perfil. Cada ID identifica o produto consolidado por loja, conforme correção de 25/10/2026; não representa necessariamente um lote. Exigir análise vigente continua sendo requisito, ainda sem garantia no Core atual (R-03).
2. Excluir todo o produto com validade negativa, aceitando temporariamente que lotes válidos consolidados possam ficar sem alerta. Com análise HIGH, MEDIUM ou LOW, validade entre 0 e 7 dias inclusive gera alerta de vencimento. Caso contrário, HIGH gera ruptura, LOW gera “Risco de excesso ou vencimento” e MEDIUM não gera alerta.
3. Um card por ID, com ação do risco prioritário. Ordenar vencimento antes de ruptura e excesso; dentro de vencimento, menor prazo primeiro; nos demais, nome. Mostrar todos com rolagem.
4. Nome em até duas linhas com reticências e texto completo acessível; nome, categoria, ID e estoque são obrigatórios. Aceitar frações e preservar até três casas decimais sem arredondar para inteiro. Por enquanto, exibir categoria e quantidade numérica sem sufixo de unidade. Validade: “Vence hoje”, “Vence em X dias” ou “Não informada”.
5. Ambos os cargos veem os mesmos alertas. “Criar promoção” abre promoção para vencimento/excesso, exceto quando a análise for MEDIUM: nesse caso ocultar a ação principal e manter o card e FEFO. “Criar pedido” abre pedido para ruptura. Levar o produto selecionado e respeitar permissões do destino, sem mutação ao navegar. Promoção/pedido existente não oculta o alerta; o destino deve evitar duplicações. Percentual e período pertencem ao fluxo de promoção.
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
| O-04 | Correspondências de loja e restrição Core ao escopo autorizado confirmadas nas respostas de 25/10. `store_id` disponível em produtos/fluxos; implementação mobile ainda resolve nome por branches. | Confirmed — contrato informado; integração sem validação | Responsável Core via solicitante, 25/10/2026 |
| O-05 | Funcionário e gerente veem os mesmos alertas e abrem promoção/pedido, respeitando permissões de cada fluxo. Ação em andamento não oculta alerta enquanto houver risco. | Confirmed | Solicitante, sexta e oitava rodadas |
| O-06 | Textos/campos do card mantidos. Cada ID consolida produto/loja, corrigindo premissa anterior por lote. Estoque inteiro e exclusão de validade negativa implementados, mas reabertos em R-01/R-02 por frações válidas e consolidação de lotes. | Open — regras em revisão | Responsável Core via solicitante, 25/10/2026 |
| O-07 | Navegação entregue com produto selecionado, rótulos aprovados e bloqueio de toques repetidos na Home, sem mutações. Destinos existentes não têm submissão Core; para entradas pelos alertas, envio informa indisponibilidade e não navega para sucesso simulado. Submissão real e prevenção de ações duplicadas continuam pendentes em seus fluxos. | Open — submissão dos destinos pendente | Solicitante, terceira, oitava e décima primeira rodadas; Mobile/Core para integração |
| O-08 | Manter “Ajuda do FEFO” visível; ao toque, mensagem breve “Ajuda do FEFO indisponível no momento”, sem chamada ao chat. | Confirmed | Solicitante, terceira e nona rodadas |
| O-09 | Atualizar ao entrar na Home e retornar de promoção/pedido; não recarregar por rotação nem simples retorno do background. Carga substitui a lista, sem mostrar dados anteriores. Isolamento de respostas por sessão/loja deve respeitar o mecanismo existente e ser verificado. | Confirmed | Solicitante, oitava a décima rodadas; testes de isolamento em AC-06 |
| O-10 | Carregamento local à seção, substituindo a lista ao concluir. Vazio, erro e parcial com textos aprovados na nona rodada. Falha/todos inválidos: erro e tentativa manual, sem repetição automática de negócio. Itens inválidos omitidos e registrados no Logger. Faltam detalhes técnicos de validação e falha de dependências na composição das consultas. | Open — parcialmente definido | Solicitante, sexta/nona rodadas; Mobile/Core para contrato e validação |
| O-11 | Core retorna histórico sem garantia de vigência ou versão; registro único também não comprova atualidade. Produtos são consolidados por loja. Consultas por store_id possíveis. Requisito de análise vigente continua sem contrato; sem escolha automática no cliente. LOW e ação para MEDIUM com validade requerem revisão em R-04/R-06. | Open — dependência Core confirmada | Responsável Core via solicitante, 25/10/2026 |
| O-12 | Manter sem issue até identificar ou criar task específica. Spec Ready for implementation por determinação do solicitante, com pendências técnicas explícitas. | Confirmed | Solicitante, rodada final em 21/10/2026 |

## Critérios de aceitação e evidências planejadas

Critérios baseados nas regras aprovadas; evidências ainda planejadas, sem testes executados. Seguir Red–Green–Refactor na implementação e verificar os contratos técnicos pendentes.

| ID | Dado / quando / então observável | Dependências | Teste ou verificação planejada |
| --- | --- | --- | --- |
| AC-01 | Dado usuário elegível com loja vinculada, ao carregar a seção, exibir somente itens autorizados da loja, conforme elegibilidade e papel acordados. | O-01, O-04, O-05 | Unitários de seleção e estado; integração de autorização Core com duas lojas e papéis relevantes. Filtro local não comprova autorização do servidor. |
| AC-02 | Com análise vigente, dias 0 a 7 geram vencimento inclusive para MEDIUM; dias negativos excluem. Fora da janela, HIGH gera ruptura e LOW excesso; MEDIUM sem alerta. Sem análise, omitir. Um card por ID com risco prioritário; vencimento por prazo, demais por nome; todos com rolagem. | O-01, O-02, O-03, O-11 | Unitários com dias -1, 0, 7, 8 e nulo, HIGH/MEDIUM/LOW e ausência de análise; IDs distintos de mesmo nome; múltiplos riscos. |
| AC-03 | Dado item elegível, renderizar nome em até duas linhas com texto completo acessível, categoria e estoque com até três casas decimais, sem arredondar para inteiro, com unidade correspondente ao produto conforme contrato a confirmar. Mostrar textos de validade aprovados. Ausência de campo obrigatório invalida item; fração válida não. | O-06, R-01 | Testes existentes de rejeição de frações precisam ser adaptados à decisão de 25/10; testes de precisão/unidade, binding e revisão visual permanecem planejados, sem execução. |
| AC-04 | Ao tocar “Criar promoção” em vencimento/excesso, abrir promoção com o produto selecionado; em ruptura, “Criar pedido” abre pedido. Sem mutação ao navegar e sem navegação repetida por toques consecutivos. “Ajuda do FEFO” mostra a mensagem breve aprovada, sem `/chat`. Submissão dos destinos permanece pendente. | O-05, O-07, O-08 | Verificação instrumentada/manual de navegação, contexto do produto e mensagem ainda pendente; necessária para avaliar integração visual dos destinos. |
| AC-05 | Dado carregamento, resposta vazia ou falha, apresentar estados distintos e recuperação conforme política aprovada, sem tratar erro como lista vazia. | O-09, O-10, O-11 | Unitários de estado e recuperação para rede/timeout, 401/403, 429, 5xx, corpo inválido e falha parcial; instrumentado dos estados visíveis. |
| AC-06 | Dada atualização ou mudança de sessão/loja, aplicar a política de recarga e impedir publicação de resultados pertencentes ao contexto anterior. | O-04, O-09 | Unitários com respostas atrasadas e mudança de sessão/loja; instrumentado de retorno à Home e recriação conforme política decidida. |

## Impacto técnico

Arquivos provavelmente afetados na futura implementação: `HomeFragment.kt`, `fragment_home.xml`, `item_alerta.xml` e recursos de texto. A integração deverá respeitar `presentation -> domain <- data`, Views/ViewBinding/ViewModels e Koin, usando o cliente Core autenticado existente. Modelos e operações necessários só serão definidos depois das decisões; este rascunho não autoriza novas camadas ou módulos.

Dependência externa: API Core/DS_Backend e seu snapshot ERP. As fontes consultadas são locais; comportamento real, autorização por loja e compatibilidade das respostas continuam sem validação. Não registrar credenciais, URLs privadas, payloads reais ou dados pessoais. Não há migração, flag ou persistência decidida.

## Resultado da implementação

Implementação mobile autorizada em 21/10/2026, com commits separados e proibição explícita de executar testes ou validações. Nenhum build, teste, lint, formatter, preview, smoke test ou chamada à API real foi executado nesta entrega. A passagem Red–Green não foi executada por essa instrução; testes escritos não são evidência de aprovação. Não há declaração de cobertura ou compatibilidade do ambiente.

- Domínio: seleção e ordenação dos riscos por produto/lote, sem geração de análises ou ações.
- Dados: cliente Core existente; resolução de filial pelo vínculo SQL do perfil e filtro documentado de produtos; consultas de análises por `product_id`, com até quatro chamadas concorrentes. Mapeamento fora da thread principal; estoque fracionário, campos obrigatórios ausentes, identidade duplicada e análise ambígua são rejeitados com logs técnicos sem payload/identificadores pessoais.
- Contrato de análise vigente: lista vazia significa ausência de análise; exatamente uma análise `ANALYZED` com produto/tipo compatíveis é aceita. Histórico com múltiplas análises é incompatível, sem escolher por ordem, ID ou data; o item é omitido com aviso parcial, ou erro se todos forem inválidos. O Core local consultado retorna histórico, portanto O-11 permanece uma dependência real da integração.
- Apresentação: ViewModel com estado em memória e proteção de publicação por geração de sessão/loja, coleta pelo lifecycle da View, RecyclerView/ListAdapter, carregamento/erro/vazio/parcial, retry manual, ações e mensagem FEFO. Rotação mantém o estado; destruição da View por navegação solicita nova carga ao retornar. Retorno simples do background não solicita carga.
- Destinos: produto selecionado preenchido nas telas de promoção/pedido, sem nome/quantidade/validade fixos para essa entrada. Preços de exemplo ocultos na promoção desse produto. Submissão não integrada foi explicitamente sinalizada como indisponível, sem confirmação fictícia de envio; prevenção de duplicações na submissão real não foi implementada por ausência do fluxo/contrato.
- Testes adicionados, **não executados**: `LoadPriorityAlertsUseCaseTests` (AC-02), `RetrofitPriorityAlertsRepositoryTests` (AC-01/03/05, incluindo frações, contrato ambíguo, filial incompatível e cancelamento) e `HomeViewModelTests` (AC-05/06: retenção, recarga por retorno, tentativa manual e descarte na invalidação da sessão). Não substituem testes instrumentados ou confirmação de autorização no servidor.

O-04, O-07 e O-11 continuam abertos nos termos acima. O-10 tem tratamento local implementado, mas compatibilidade real das respostas e verificação da interface permanecem pendentes. Nenhum arquivo do DS_Backend foi alterado; nenhuma task Jira foi criada ou atualizada.
