# Criar nova interferência — pedido de compra ou promoção

**Status:** Ready for implementation — mobile alinhado ao Core 3.0.0; validação real na versão final pendente  
**Issue:** none  
**Product decision owner:** solicitante

## Problem and goal

Permitir ao funcionário criar um pedido de compra ou uma promoção para produto da
loja e encaminhar a interferência ao gerente, com confirmação real e recuperação
de falhas sem duplicar a geração.

Fonte: solicitação de 29/10/2026 e refinamentos interativos. A orientação posterior
“Sobre os conflitos, siga Core” substitui as decisões anteriores conflitantes:
gerente não cria, elegibilidade depende de risco, os campos exigidos pelo Core
são mantidos, pendências bloqueiam duplicidade e não se limita fim da promoção
pela validade que a resposta pública não permite reconstruir com segurança.

## Scope

**In scope:** entradas pela Home e pelos atalhos de Interferências; seleção de
produto/análise elegível; formulários; confirmação; geração e encaminhamento;
consulta mínima para mostrar ação existente/conferir resultado; estados de erro,
rascunho e navegação. A futura implementação deve atualizar as regras conflitantes
de entrada da Home no mesmo trabalho, sem mudar a classificação Core.

**Out of scope:** criação por gerente, aprovação/rejeição, alteração do backend,
execução no ERP, notificações, edição/cancelamento de ações, criação offline,
persistência de rascunho em disco e reformulação completa da listagem.
Nesta entrega somente a documentação é alterada; implementação não autorizada.

## Expected behavior

### Fonte contratual e precedência

A investigação fornecida pelo solicitante inspecionou contrato local Core 3.0.0,
de 26/10/2026, código e testes, sem executá-los. Não verifica o deploy.
Referências: `DS_Backend/api-contract.md`, `ActionController`, `ActionService`,
`ActionCreationValidator`, `ActionRequestValidator`, `ActionRepository`,
`ActionReadRepository`, `ProductRepository` e `ProductDTO`.

Esta spec prevalece, para criação, sobre a referência antiga de
[home-priority-alerts.md](home-priority-alerts.md) a HIGH/LOW, MEDIUM sem geração e
campos não suportados. Os avisos consultivos e limites de atualidade continuam:
análise atual significa último processamento, sem garantia de estoque ERP atual.
[manager-create-interferences.md](manager-create-interferences.md) trata de
análise/aprovação e não estabelece o contrato de criação.

### 1. Entrada, autorização e elegibilidade

- Somente EMPLOYEE com sessão/perfil atuais e vínculo ativo com loja cria e
  encaminha. MANAGER não oferece criação e não tenta gerar/aprovar localmente.
  Aprovação gerencial de solicitações encaminhadas pertence a outro fluxo.
- Entradas pela Home e pelos atalhos de Interferências usam a mesma regra:
  STOCKOUT permite ORDER; NEAR_EXPIRY ou EXPIRATION, sem EXPIRED, permite
  PROMOTION. HIGH/MEDIUM/LOW são velocidade de vendas e não habilitam nem
  desabilitam o tipo por si só. Se ambos os tipos forem elegíveis, permitir
  escolher qual criar e informar explicitamente `action_type`.
- Selecionar produto da loja e análise atual, ANALYZED, do classificador atual.
  Não substituir silenciosamente uma análise rejeitada por outra. Não criar
  com produto fixo ou dados de demonstração. Navegar não faz escrita remota.
- Mostrar loja/usuário do perfil e resumo real do produto. Manter estoque decimal
  de até três casas efetivas, sem inferir unidade. Quantidade de pedido tem regra
  própria inteira. Contexto inválido/de outra sessão ou loja bloqueia escrita.
- Carregamento, erro de consulta e ausência de elegíveis são estados distintos;
  erro permite retry manual e vazio válido não mostra dados fictícios.

### 2. Pedido de compra

- Título/nome do tipo: “Pedido de compra”.
- Solicitar quantidade obrigatória: inteiro positivo, máximo 2147483647;
  rejeitar fração, zero, negativo e overflow, sem arredondar. Não inventar unidade
  nem sugestão automática; quantidade é informada pelo usuário.
- Solicitar data desejada obrigatória, hoje ou futura em America/Sao_Paulo.
- Enviar `flow_id`, `action_type=ORDER`, `quantity` e `desired_order_date`.
  Datas de transporte são civis `YYYY-MM-DD`. Não enviar campos de promoção.

### 3. Promoção

- Solicitar desconto obrigatório entre 0 e 100 inclusive, até duas casas decimais.
  Aceitar entrada localizada e converter para decimal JSON sem arredondamento
  silencioso. Não enviar quantidade nem data desejada de pedido.
- Usuário escolhe data inicial e final obrigatórias: início hoje ou futuro;
  fim igual ou posterior ao início. Datas inclusivas, civis `YYYY-MM-DD`, usando
  America/Sao_Paulo para “hoje”. Revalidar no envio, inclusive após virada do dia.
- Enviar `flow_id`, `action_type=PROMOTION`, `discount_percentage`,
  `promotion_valid_from` e `promotion_valid_until`. Não enviar duração junto;
  a alternativa Core por duração não faz parte da interface escolhida.
- Seguir o Core: não limitar o fim à validade do produto nem bloquear apenas
  por expiration_days ausente. A restrição por EXPIRED continua na elegibilidade
  da análise. expiration_days não é data absoluta nem prova de estoque atual.
- Preços de referência/promocional são retornados e persistidos pelo Core.
  Não usar preços de exemplo nem apresentar cálculo local como valor confirmado.
  Erros de campo preservam rascunho, indicam motivo e impedem envio inválido.

### 4. Revisão, duplicidade e envio

1. Confirmar tipo, produto e dados preenchidos: quantidade/data no pedido ou
   desconto/início/fim na promoção. Cancelar preserva rascunho sem escrita.
2. Consultar ações da loja/tipo, abrangendo análises e comparando product_id.
   GENERATED, IN_EMPLOYEE_TRIAGE e SENT_TO_MANAGER de outra análise bloqueiam
   nova geração para mesmo produto/loja/tipo; mostrar a existente.
   APPROVED e REJECTED liberam outra análise. Na mesma análise/tipo, o Core
   reutiliza a existente, inclusive final, sem atualizar parâmetros: apresentar
   os dados reais e não prometer nova criação nem alteração dos valores.
3. Ação existente GENERATED/IN_EMPLOYEE_TRIAGE pode ser retomada para encaminhar,
   com revisão dos valores persistidos; nunca regenerar para editar o rascunho.
   SENT_TO_MANAGER já está encaminhada. Estados finais não são reenviados.
4. Gerar com `POST /actions/generate`. HTTP 201 sozinho não comprova criação:
   validar flow_id e ação retornada, identidade/tipo/loja/produto/estado aplicáveis.
   `generated_actions` vazio informa que nenhuma ação foi gerada e permite
   atualizar a consulta de elegibilidade, sem sucesso ou PATCH com id inventado.
   Corpo incompatível após escrita é resultado incerto.
5. Guardar id e valores reais retornados em memória na sessão. Para GENERATED
   ou IN_EMPLOYEE_TRIAGE, encaminhar a existente com
   `PATCH /actions/{id}/status` e `{"status":"SENT_TO_MANAGER"}`.
   Não é necessário passar por triagem antes. Geração e encaminhamento são
   transações separadas; sucesso da primeira não é sucesso do fluxo completo.
6. Se gerar e falhar o encaminhamento com resultado conhecido, manter a ação e
   oferecer tentativa manual somente do encaminhamento, respeitando seu estado.
   Não repetir geração. Se houver timeout/desconexão ou corpo inválido depois
   de qualquer escrita, informar resultado incerto e encaminhar à lista para
   conferência; não repetir automaticamente. Ausência na lista não prova falha.
7. Somente confirmação de SENT_TO_MANAGER permite “Enviado para aprovação”.
   Mostrar resumo dos valores persistidos, que podem diferir do rascunho se a
   geração reutilizou ação. Não afirmar aprovação, promoção ativa ou execução ERP.
8. “Voltar para Interferências” retorna ao hub do funcionário e atualiza a lista.
   Falha de atualização é erro de consulta e não desfaz/reenvia a criação.
   Conclusão limpa o rascunho; formulário concluído não fica pronto para reenvio.

### 5. Erros e recuperação

- 400 INVALID_REQUEST: apresentar validação e preservar valores. Não expor
  mensagens técnicas sem tratamento nem tratar recusa como sucesso.
- 401: recuperação de sessão existente, sem replay automático de escrita.
- 403: informar ausência de permissão; sem loop de retry.
- 404 FLOW_NOT_FOUND: contexto indisponível/fora do escopo, retornar à seleção.
- 409 CONFLICT: conferir ação existente/análise atual por consulta; não trocar
  flow_id e reenviar automaticamente. Preservar valores para revisão, sem
  transportar parâmetros para outra ação silenciosamente.
- Falhas de leitura permitem retry manual. Escritas não têm retry automático,
  inclusive após refresh. Não prometer idempotência geral: repetição de geração
  usa análise/tipo mas não atualiza valores; análise superada ainda pode falhar.
- GET /actions aceita store_id, action_type, status e flow_id; não tem filtro de
  produto/paginação. Omitir flow_id para abrangência e comparar product_id.
  Lista é evidência de estado observado, não marcador terminal de operação.
- Logout/troca de geração ou loja invalida contexto, rascunho e resultados antigos.
  O cliente Core usa Bearer privado existente, sem Cookie/CSRF ou credenciais
  expostas na UI/logs. Consultas não garantem unicidade contra outros dispositivos;
  servidor revalida transacionalmente e pode recusar mesmo após pré-checagem.

### 6. Ciclo de vida e acessibilidade

- Rascunho inclui produto/análise, quantidade, desconto e datas aplicáveis.
  Mantê-lo apenas em memória por sessão, entre abas, rotação e background;
  perder ao encerrar processo/logout. Revalidar contexto/datas antes de enviar.
- Voltar com preenchimento pede confirmação de descarte. Confirmar limpa e
  retorna à origem; cancelar mantém valores/tela. Sem rascunho, voltar direto.
- Durante geração e encaminhamento, bloquear Voltar e troca de abas até resultado
  ou timeout; impedir submissão concorrente e toques repetidos. Background,
  encerramento e revogação da sessão continuam possíveis. Após resultado/timeout,
  liberar navegação e aplicar recuperação correspondente.
- Limitar cada chamada de escrita a 30 segundos de duração total: geração e
  encaminhamento têm limites independentes, não um orçamento compartilhado.
  Aplicar no caminho desta funcionalidade sem alterar limites de outras operações
  por consequência. Não confundir timeout com cancelamento remoto; ao atingir
  o limite sem resultado confirmado, aplicar a recuperação de resultado incerto.
- Seguir navegação por cargo, regra de teclado e componentes existentes.
  Rótulos acessíveis, erros sem depender apenas de cor, foco no campo inválido,
  ações alcançáveis com teclado/fonte ampliada e anúncio de progresso/resultado.

## Acceptance criteria and evidence

Evidência abaixo planejada, não executada. Aplicar Red–Green–Refactor.

| ID | Observable given / when / then | Planned test or check |
| --- | --- | --- |
| AC-01 | Home e hub habilitam ORDER por STOCKOUT e PROMOTION por NEAR_EXPIRY/EXPIRATION sem EXPIRED; HIGH/MEDIUM/LOW não decidem tipo; ambos permitem escolha explícita. Abrir não escreve. | Unitários de matriz risco/velocidade e instrumentados de ambas as entradas. |
| AC-02 | Contexto inválido, outra loja/sessão ou cargo MANAGER impede criação; EMPLOYEE ativo pode prosseguir. | Unitários de contexto e instrumentados por cargo; contrato de 403. |
| AC-03 | Pedido exige inteiro 1..2147483647 e data hoje/futura; não envia campos de promoção. | Unitários de limites, frações, overflow e data; contrato do JSON e instrumentados. |
| AC-04 | Promoção exige desconto 0..100 até duas casas e datas inclusivas com início hoje/futuro e fim >= início em São Paulo; não limita fim à validade nem envia campos de pedido/duração. | Unitários de limites/localização/virada de dia e contrato; instrumentados de campos. |
| AC-05 | Confirmação mostra dados preenchidos; cancelar não escreve e preserva valores; confirmar repetidamente inicia uma operação. | Unitários de estado/concorrência e instrumentado do diálogo. |
| AC-06 | Pendência de outra análise bloqueia; aprovada/rejeitada não bloqueia outra análise; mesmo flow/tipo reutiliza valores reais sem editar a existente. | Contrato simulado por estado/identidade e instrumentado de apresentação. |
| AC-07 | Gerar persiste GENERATED e encaminhar usa o id retornado; somente SENT_TO_MANAGER exibe sucesso. Gerada com encaminhamento falho permite repetir apenas PATCH. | Testes de repositório/use case/ViewModel com falha entre chamadas e recuperação. |
| AC-08 | HTTP 201 com lista vazia não mostra sucesso nem envia PATCH; corpo incompatível após escrita produz incerteza. | Contrato de retorno vazio/inválido e teste de estado. |
| AC-09 | Timeout de geração/PATCH informa incerteza e leva à lista sem reenvio; lista vazia/com falha não prova ausência de escrita. | Integração simulada de gravação seguida de timeout/consulta tardia e instrumentado. |
| AC-10 | Erros 400/401/403/404/409 seguem recuperação definida, sem replay automático nem troca silenciosa de análise. | Contrato de erros e testes de transições/refresh. |
| AC-11 | Resposta atrasada de outra sessão/loja não publica estado; logout limpa rascunho e ação em memória. | Unitários com resposta atrasada e instrumentado de logout. |
| AC-12 | Rascunho completo persiste entre abas/rotação/background e é perdido após processo/logout; Voltar confirma descarte. | Unitários e instrumentados de ciclo de vida, campos e diálogo. |
| AC-13 | Envio bloqueia Voltar/abas e repetição até resultado/timeout, sem impedir revogação da sessão; geração e encaminhamento têm limite total independente de 30 segundos por chamada. | Tempo controlado verificando limite de cada chamada e instrumentados de navegação. |
| AC-14 | Sucesso usa valores persistidos e “Enviado para aprovação”; retorno atualiza lista sem repetir criação mesmo se a consulta falhar. | ViewModel, mapper e instrumentado de conclusão/retorno. |
| AC-15 | Leitor de tela, fonte ampliada e teclado permitem preencher, corrigir e enviar ambos os formulários. | Manual justificado com TalkBack, escala de fonte e teclado; instrumentação de foco/rótulos. |
| AC-16 | Carga, erro e vazio de elegibilidade são distintos; conflito de análise solicita nova consulta sem escrita implícita. | Unitários de estado e instrumentados de seleção/recuperação. |
| AC-17 | A funcionalidade somente é considerada concluída após teste real na versão final de pedido, promoção e permissões: funcionário cria/encaminha dentro do escopo e gerente não cria; recusas de cargo/loja não produzem sucesso. Não exigir ambiente separado de homologação. | Teste na versão final, registrando versão, cenários e resultados sem segredos ou dados pessoais. Testes locais/simulados não substituem essa evidência. |

## Technical impact

Futura implementação afeta presentation/order, promotion, interferences e entradas
Home, navegação e layouts; regras em domain e DTOs/repositório Core em data.
Preservar Views/XML/ViewBinding, ViewModels, Koin, pacote e dependências do README.
Sem módulos antecipados, migração ou armazenamento persistente do rascunho.
Atualizar a spec da Home no mesmo trabalho para refletir elegibilidade por risco.

Riscos conhecidos: duas transações com resultado parcial; histórico consultivo;
lista sem marcador terminal após timeout; valores antigos em geração reutilizada;
checagem de duplicidade mobile não garante concorrência; ausência de estados de
execução/conclusão e de limite contratual pela validade. Não corrigir backend
neste escopo nem declarar que aprovação executa operações no ERP.

## Decisions and open questions

| Item | Status | Source or decision owner |
| --- | --- | --- |
| Somente funcionário cria/encaminha; gerente aprova em outro fluxo. | Confirmed | Orientação posterior do solicitante: seguir Core. |
| Risco determina elegibilidade, inclusive em MEDIUM; parâmetros obrigatórios atuais são mantidos. | Confirmed | Solicitante e investigação Core 3.0.0. |
| Datas explícitas, São Paulo, sem limite adicional pela validade. | Confirmed | Seguir Core resolve conflito anterior; sem data absoluta confiável. |
| Duplicidade segue pendências Core e unicidade análise/tipo; não existe estado em andamento. | Confirmed | Seguir Core substitui regra anterior de aprovadas. |
| Encaminhamento separado; recuperação repete somente PATCH quando resultado conhecido; incerteza vai à lista. | Confirmed | Contrato investigado e experiência já aprovada. |
| Rascunho em memória, descarte confirmado, bloqueio de Voltar/abas durante envio, nome Pedido de compra e retorno com atualização. | Confirmed | Respostas do solicitante, preservadas onde compatíveis. |
| Timeout total de 30 segundos por chamada de escrita, independente para geração e encaminhamento. | Confirmed | Solicitante, 29/10/2026; verificar configuração na implementação. |
| Não há ambiente de homologação; pedido, promoção e permissões serão testados na versão final, obrigatoriamente antes de considerar a funcionalidade concluída. | Confirmed — execução pendente | Solicitante, 29/10/2026; substitui exigência de ambiente de homologação; conclusão exige AC-17. |
| Evidência dos testes na versão final, comportamento do deploy, concorrência e virada de dia. | Pending — validação de integração | Registrar resultados durante a validação final; ausência de ambiente separado não é bloqueio para implementar. |

## Implementation outcome

Somente documentação criada/refinada. Nenhuma implementação, teste, build ou
chamada de API executada nesta tarefa. Investigação fornecida foi somente leitura;
testes citados nela não foram executados. Ready for implementation indica escopo
e regras definidos, não entrega, aprovação de backend ou validação em produção.
A funcionalidade não pode ser marcada como concluída sem o teste real na versão
final exigido em AC-17, além das verificações pertinentes da implementação.
