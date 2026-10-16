# Big numbers da Home com dados da API Core

**Status:** Draft — decisões de produto incorporadas; contrato por loja e identidade offline ainda bloqueiam implementação.
**Issue:** none
**Product decision owner:** solicitante (decisões de produto recebidas em 16/10/2026)

## Problema e objetivo

A Home exibe valores fixos para Vencimento curto, Ruptura/Excesso e Ações ativas.
Substituir esses exemplos pelos dados reais da Core, com estados observáveis de
carregamento, indisponibilidade e recuperação, respeitando a sessão existente.
Fonte do requisito: solicitação de 16/10/2026 nesta conversa. Esta entrega prepara
a especificação; não autoriza iniciar a implementação antes de resolver todos os Open.

## Fontes e situação verificada

Inspeção dos arquivos locais em 16/10/2026; os testes citados foram lidos, não executados.

| Evidência | Fonte |
| --- | --- |
| Arquitetura, fronteiras e processo | [README](../../../README.md), [contexto](../context.md), [processo SDD](../README.md) |
| Três cards com valores fixos e Fragment sem ViewModel | `app/src/main/res/layout/fragment_home.xml`, `presentation/home/HomeFragment.kt` |
| Contrato do dashboard | Repositório irmão `DS_Backend`, `api-contract.md`, seção 6.1 |
| Rota, tipos e cálculo implementados | `DS_Backend/src/main/java/com/quistock/ds_backend/`: `controller/DashboardController.java`, `model/dto/DashboardSummaryDTO.java`, `service/DashboardService.java` |
| Exemplos de sucesso, vazio e ERP indisponível | `DS_Backend/src/test/java/com/quistock/ds_backend/`: `controller/DashboardControllerTest.java`, `service/DashboardServiceTest.java` |
| Endpoint protegido, sem filtro de usuário no serviço | `DS_Backend/.../config/SecurityConfig.java`, `DashboardController.java`, `DashboardService.java` |
| Configuração atual e cliente autenticado | `app/src/main/res/values/config.xml`, `app/di/RetrofitModule.kt`, `data/remote/auth/CoreSessionInterceptor.kt` |
| Sessão, gerações e proteção contra resultados atrasados | `domain/usecase/SessionUseCase.kt`, `presentation/activity/MainActivity.kt` |
| Cancelamento do fluxo anterior de indicadores/cache | [QUIS-128, Removal of the cancelled indicator feature](QUIS-128-login-client-side.md#removal-of-the-cancelled-indicator-feature-2026-10-15) |

Caminhos de packages Mobile acima são relativos a
`app/src/main/java/com/quistock/quistock/`. A anotação de escopo atual da QUIS-128
prevalece sobre referências históricas a indicadores simulados e cache Room.
O código atual lê `core_base_url` do recurso; a menção do README a `CORE_BASE_URL`
via ambiente está desatualizada para esse cliente. Não reproduzir endereços privados.

## Escopo

**Incluído:** leitura do resumo, mapeamento para os três cards, configuração explícita
do Retrofit Core, integração ao ciclo de vida da Home, estados e erros, isolamento
de sessão, DI Koin e testes vinculados aos critérios.

**Excluído:** lista de alertas prioritários, perfil/nome da loja/saudação, novas rotas
de navegação ou cliques nos cards, alterações de regras no backend, geração ou
mutação de ações, chamadas ERP diretas, redesign da Home, mudanças no chatbot,
reescrita da autenticação e consulta dos demais indicadores do resumo.
Cache persistente por usuário e loja está incluído. O resumo deve ser exclusivo da
loja do usuário; o contrato necessário no DS_Backend é uma dependência obrigatória,
e sua implementação não está incluída nesta entrega documental do Mobile.

## Contrato real da integração

- Ambiente inicial: desenvolvimento, usando exclusivamente `R.string.core_base_url`
  de `config.xml`, com `trim()` e validação HTTP(S). Não criar URL paralela ou fallback
  para Auth, chatbot ou uma URL fictícia.
- Método/rota Core: `GET /dashboard/summary`; sem body e sem parâmetros no contrato atual.
  **Esse contrato global ainda não atende ao escopo aprovado.** Não consumir totais
  globais como fallback. A rota/parâmetros e os metadados do futuro contrato por loja
  devem ser acordados e documentados no DS_Backend antes de fixar a interface Retrofit.
  Na interface Retrofit usar caminho relativo `dashboard/summary`, com base terminada
  em `/`, preservando qualquer prefixo público do gateway. O `/` inicial descreve a
  rota interna do backend e não deve apagar o prefixo configurado.
- Enviar `Authorization: Bearer` pelo `CoreSessionInterceptor` existente. Não expor
  tokens a domínio/UI, não enviar cookies Auth e não consultar `/csrf` para essa leitura.
- Sucesso: HTTP 200 com objeto JSON. Consumir somente os quatro campos abaixo;
  ignorar campos adicionais. Cada campo é obrigatório, inteiro não negativo,
  compatível com `Long` Kotlin (os quatro campos são `long` no DTO Java).
- Campo ausente/nulo, negativo, fracionário, fora de `Long`, JSON inválido, body vazio
  ou status de sucesso diferente do contratado não devem virar zero ou sucesso parcial.
  Retornar falha de contrato. Não publicar parte dos cards de uma resposta inválida.
- O backend documenta 503 `ERP_UNAVAILABLE`; tratá-lo como indisponibilidade do serviço.
  Mensagens técnicas do body não são copy da interface.

| Campo | Regra verificada no backend | Destino |
| --- | --- | --- |
| `near_expiry_products` | Produtos por filial, estoque positivo e validade mais próxima <= limite configurado (padrão 30 dias). O código não impõe limite inferior; não recalcular nem excluir vencidos no app. | `tvNumeroVencimento` |
| `stockout_products` | Estoque consolidado <= 0 | Somar com `overstock_products` em `tvNumeroRuptura` |
| `overstock_products` | Estoque consolidado > estoque mínimo; não substituir por outro conceito de excesso | Somar com `stockout_products` em `tvNumeroRuptura` |
| `active_actions` | Status GENERATED, IN_EMPLOYEE_TRIAGE, SENT_TO_MANAGER ou APPROVED | `tvNumeroAcoes` |

Contagens de produtos são por produto e filial, não por lote ERP. `active_actions`
não equivale a `suggested_actions`. Não derivar esses números de risco, total de
produtos, lista de alertas, nem somar lotes localmente. Somar ruptura e excesso,
validando overflow de `Long`. O solicitante confirmou que as duas categorias são
mutuamente exclusivas para um produto em uma loja; a soma representa produtos únicos
nessa loja. Essa garantia deve constar no contrato e nos testes do backend por loja.
Fixture sintética do contrato: vencimento=4, ruptura=2, excesso=3, ações=5.
Resultado esperado nos três cards: 4, 5 e 5. A Core retorna zeros quando não há dados;
isso é sucesso válido e não equivale a ausência de resposta.

## Comportamento esperado

### Carregamento, atualização e apresentação

- Substituir números de exemplo em runtime por estado sem dados; exemplos podem
  permanecer apenas como `tools:text`. Nunca exibir `3`, `2`, `4` como fallback.
- Publicar o conjunto dos cards atomicamente após validação do resumo e da geração
  da sessão. Valores reais iguais a zero devem aparecer como `0`.
- Buscar ao entrar na Home, inclusive ao retornar de outra tela, e ao tocar em
  “Tentar novamente”. Carregar primeiro o cache autorizado disponível e então buscar
  atualização remota, mesmo quando os dados são da data atual. Reobservar estado,
  rotacionar ou recriar a View no mesmo destino não equivale a uma nova entrada.
  Se uma entrada coincidir com operação já em andamento, compartilhar essa operação.
- Enquanto houver requisição, indicar carregamento na região do resumo e impedir
  novas tentativas concorrentes. Rotação/recriação da View não duplica uma operação
  em andamento no mesmo ViewModel. Cancelamento não vira erro visível.
- Nunca substituir números válidos por zero em uma falha. Sem valor disponível,
  apresentar `—` e aviso. Com cache autorizado, manter os valores anteriores junto
  ao aviso da falha e, quando aplicável, ao aviso de desatualização.
- Rótulos atuais e posição dos três cards são preservados. Os dados são exclusivos
  da loja atribuída ao usuário, sem totais globais ou escolha automática de outra loja.
- Exigir uma única loja ativa atribuída. Quando a ausência de loja elegível for
  confirmada pelo servidor, mostrar “Nenhuma loja vinculada ao seu usuário.” e `—`
  nos três cards, sem resumo global, seletor de lojas ou valores do cache anterior.
  Falha de rede ao consultar a atribuição não equivale a ausência confirmada de loja;
  nesse caso, aplicar o fallback offline do escopo previamente autorizado.
- Formatar contagens inteiras no locale do app, sem abreviação ou perda de precisão.
  Strings e descrições acessíveis ficam em recursos. TalkBack deve anunciar rótulo,
  valor ou indisponibilidade e permitir acessar a ação de retry.

### Cache persistente por usuário e loja

- Persistir apenas o último resumo integralmente validado, os identificadores
  opacos de usuário/loja, a versão do registro e o instante da última atualização
  válida. Não armazenar token, email, nome ou resposta JSON completa no cache.
- Dados recebidos em uma data continuam válidos nessa mesma data. Em datas
  posteriores, continuar mostrando o cache com aviso persistente informando a data
  da última atualização válida; o aviso de desatualização não substitui erros de rede.
  Usar exclusivamente o fuso fixo `America/Sao_Paulo` para a data de recepção e
  a data atual; timezone do dispositivo e da loja não alteram essa comparação.
  Aviso aprovado: “Dados possivelmente desatualizados. Última atualização: {data}.”
  Formatar {data} no locale do app, sem ocultar os números antigos.
- Reavaliar validade ao renderizar/retomar a Home e na virada de data com a Home
  visível. Isso só atualiza o aviso; não dispara busca automática à meia-noite.
- Sucesso remoto substitui atomicamente as quatro contagens e a data de atualização.
  Falha, resposta inválida ou cancelamento não alteram a data do último sucesso.
- O cache deve sobreviver à morte do processo e estar disponível na reabertura
  offline para a mesma identidade autorizada. Nunca selecionar “o último usuário”
  sem vínculo verificável com a sessão restaurada. SessionSnapshot fornece geração,
  não identidade durável; `UserPreferences` legado/Firebase não comprova identidade Core.
  O mecanismo seguro de vínculo offline deve ser fechado no contrato D5.
- Separar a chave por usuário e loja, invalidando o escopo anterior quando houver
  troca de identidade/loja. Respostas de uma geração encerrada não podem gravar cache.
  Expiração definitiva e saída invalidam imediatamente leitura/renderização e removem
  os registros correspondentes. Falha transitória de refresh preserva cache e vínculo.
  Não manter lock da sessão durante rede/disco; definir commits protegidos contra
  corrida com invalidação e testar gravação pendente após limpeza.
- Persistência fica em `data`, atrás de uma porta específica do domínio; não reintroduzir
  o Room antigo nem um cache genérico. Registro versionado de resumo único por escopo
  pode usar o DataStore já adotado no projeto, sem justificar banco relacional para
  esse volume. Registro ausente, incompatível ou corrompido equivale a cache indisponível;
  não impede buscar a API. Não há cache anterior vigente a migrar.
- Falha de gravação não transforma resposta remota válida em zeros: mostrar os dados
  em memória, preservar último registro válido e expor indisponibilidade de persistência
  com a mensagem aprovada “Indicadores atualizados, mas não foi possível salvá-los
  para acesso offline.” Não alegar que a atualização foi salva para uso offline.

### Erros e sessão

- Reutilizar o fluxo de sessão: ausência de access válido exige recuperação antes
  da chamada; primeiro 401 Core aciona refresh e uma repetição após sucesso;
  segundo 401 ou rejeição definitiva segue expiração/navegação já implementada.
  Não criar outro interceptor, refresh no ViewModel ou loop de tentativas.
- Falhas transitórias de recuperação preservam sessão e usam os mesmos estados
  de erro do resumo. Verificar `SessionException.reason` antes de classificar como
  `IOException`, pois essa exceção herda de `IOException`.
- Rede: “Sem conexão com a internet. Verifique sua conexão e tente novamente.”
- Timeout: “Sua conexão está lenta. Tente novamente.”
- 5xx, incluindo 503 ERP: “Ocorreu um erro interno. Tente novamente mais tarde.”
- 403: “Você não tem permissão para consultar estes indicadores.” Sem refresh.
- 429: “Muitas tentativas. Tente novamente mais tarde.” Sem refresh. Respeitar
  Retry-After válido (segundos ou HTTP-date), bloqueando novas tentativas do resumo
  até o prazo; fim do prazo apenas reabilita o botão, sem disparar chamada.
  Header ausente/inválido ou prazo passado permite retry manual. Uma nova entrada
  na Home não deve contornar um prazo ainda ativo na execução atual do app.
- Falha de contrato/outros HTTP inesperados: “Não foi possível carregar os indicadores.
  Tente novamente.” Sem exibir body, URL, stacktrace ou credenciais.
- Mensagens específicas de 403/429/contrato e política Retry-After foram aprovadas.
  Rede/timeout/5xx reutilizam a copy estabelecida para recuperação de sessão.
- Retry usa “Tentar novamente”, desabilitado durante operação. Não fazer polling,
  retry por reconexão ou novo refresh por status diferente de 401.
- Expiração, saída ou nova geração invalidam dados exibidos e resultados em andamento.
  Verificar geração também no momento de publicar o estado, após desserialização,
  para cobrir troca de sessão depois de o interceptor receber a resposta.
  Cancelar trabalho da Home ao encerrar seu ViewModel e propagar CancellationException.
- A navegação global permanece responsabilidade da Activity/SessionViewModel.
  LocalFailure segue a navegação atual e não deve virar aviso de falta de rede.

## Impacto técnico e responsabilidades

Manter `presentation -> domain <- data`, módulo `:app`, Views/XML, ViewBinding e Koin.
Os nomes abaixo são a decomposição planejada; não são classes já existentes.

| Componente | Local | Responsabilidade única |
| --- | --- | --- |
| `HomeSummary` e resultado/falhas tipados | `domain/model/` | Quatro contagens sem DTO, Android, HTTP ou credenciais; representar falhas necessárias ao caso de uso |
| `HomeSummaryRepository` | `domain/port/` | Operação de leitura de resumo em tipos do domínio |
| `GetHomeSummaryUseCase` | `domain/usecase/` | Coordenar leitura remota/cache e validade de identidade/geração; somar ruptura e excesso com validação de overflow |
| `HomeSummaryCache` | `domain/port/` | Ler, substituir e invalidar o resumo pelo escopo usuário/loja sem expor armazenamento |
| Implementação do cache | `data/preferences/` | Persistir registro versionado e metadados atomicamente, tratar corrupção e impedir commits após invalidação |
| Política de validade diária | `domain/` | Comparar datas com relógio/fuso injetáveis, sem classes Android; produzir validade e data de atualização |
| `DashboardApi` | `data/remote/core/` | Declarar somente o endpoint Retrofit |
| `DashboardSummaryDto` | `data/remote/core/dto/` | Campos JSON e tipos de transporte obrigatórios |
| Mapper de resumo | `data/remote/core/mapper/` | Validar contagens e converter DTO em modelo de domínio; função simples, sem interface adicional |
| `RetrofitHomeSummaryRepository` | `data/remote/core/` | Chamar API, mapear resposta e classificar falhas de transporte/contrato; não decidir copy ou navegação |
| `HomeViewModel` e `HomeUiState` | `presentation/home/` | Coordenar use case, trabalho único, estado e nova tentativa; não acessar Retrofit/repository concreto nem transportar DTO |
| Formatter de apresentação | `presentation/home/` | Locale e rótulos/valores; função ou classe apenas conforme necessidade concreta |
| `HomeFragment` | `presentation/home/` | ViewBinding, observar com viewLifecycleOwner, renderizar estado e encaminhar eventos; liberar binding em onDestroyView |
| DI | `app/di/RetrofitModule.kt`, `DomainModule.kt`, `PresentationModule.kt` | Construir Retrofit com CoreHttpClient e config Core, bind repository/port, use case e ViewModel |
| Recursos | `res/layout/fragment_home.xml`, `res/values/strings.xml` | Estados visuais, copy e acessibilidade do resumo |

Não introduzir um data source que apenas delegue ao Retrofit, cache genérico ou módulos
Gradle novos. A coordenação da invalidação deve usar evento/escopo de sessão, sem acoplar
SessionUseCase a repository de indicadores ou DTO. Fechar o vínculo durável de identidade
e o contrato de loja antes de marcar Ready; não tratar geração como ID de usuário.

**Risco concreto de DI:** `retrofitSdkModule` associa ApiQualifier.Auth a core_base_url
e ApiQualifier.Core a auth_base_url; o ChatbotApi usa esse segundo Retrofit legado.
Não resolver DashboardApi pelo nome aparente desse qualificador. Construir binding Core
inequívoco usando o `CoreHttpClient` autenticado já registrado, sem redirecionar o chatbot
como efeito colateral. Testar URL e cliente efetivos, não apenas existência do binding.
Atualizar a referência de configuração no README/contexto quando implementar a integração.

## Critérios de aceitação e evidência planejada

Todos os testes abaixo são planejados para a implementação; nenhum resultado é alegado.
Critérios dependentes do contrato técnico D5 devem ser completados antes do primeiro código.

| ID | Dado / quando / então | Teste ou check |
| --- | --- | --- |
| AC-01 | Dada a config Core, ao buscar resumo, enviar GET ao caminho correto, preservando prefixo de gateway e usando o cliente autenticado; nenhuma chamada à Auth como resumo | Unitário de factory/DI e HTTP controlado com base sintética com/sem prefixo |
| AC-02 | Dado JSON válido 4/2/3/5, mapear quatro Long, ignorar campos adicionais e mostrar cards 4/5/5; overflow da soma é falha | Unitários de mapper/use case e ViewModel |
| AC-03 | Dados zeros, mostrar 0 nos cards como sucesso | Mapper/ViewModel e instrumentado Home |
| AC-04 | Dado campo ausente/nulo/negativo/fracionário, overflow ou body inválido/vazio, falhar atomicamente sem zeros artificiais | Testes parametrizados do mapper/repository com transporte controlado |
| AC-05 | Dado primeiro carregamento, não exibir números fictícios; indicar loading e impedir operação duplicada; sucesso atualiza conjunto completo | ViewModel com resposta suspensa; instrumentado da Home |
| AC-06 | Dadas entrada/retorno à Home, buscar atualização com cache imediato; recriação da View/rotação/reobservação não duplicam busca e operação existente é compartilhada | ViewModel e instrumentado de recriação/navegação |
| AC-07 | Dadas rede/timeout/5xx/503, preservar sessão/cache/data do sucesso, informar causa e permitir retry manual, sem retry ao reconectar | Repository/ViewModel e testes HTTP de recuperação da sessão |
| AC-08 | Dados 403/429/outros HTTP, seguir D4, sem refresh ou exposição de body | Repository/ViewModel; contagem de chamadas de autenticação |
| AC-09 | Dado primeiro 401, refresh existente e repetição única; segundo 401 ou refresh definitivamente inválido expiram sessão e removem estado anterior | Integrar DashboardApi/CoreSessionInterceptor com transporte controlado; regressão de navegação de sessão |
| AC-10 | Dada falha transitória de refresh sem access em memória, informar falha correta sem confundir SessionException com rede e sem expirar | Use case/ViewModel e HTTP controlado para NETWORK/TIMEOUT/SERVER/RATE_LIMIT |
| AC-11 | Dada resposta atrasada, trocar geração durante rede ou após desserialização impede publicação na sessão seguinte; dados anteriores são invalidados | Concorrência determinística com barreiras no use case/ViewModel; teste de navegação |
| AC-12 | Dada destruição da View/cancelamento, não acessar binding destruído nem emitir erro por cancelamento; ViewModel encerrado cancela trabalho | Unitário com cancelamento e instrumentado de ciclo de vida |
| AC-13 | Dados valores grandes/locale e estados sem dados, apresentar inteiro preciso e descrição acessível; TalkBack alcança retry | Unitário do formatter; instrumentado de texto/descrições e check manual TalkBack (leitura por tecnologia assistiva) |
| AC-14 | Dada resolução Koin, obter fluxo Home completo com CoreHttpClient, mantendo chatbot isolado e domínio sem imports de infraestrutura | AppModulesTest ampliado e revisão das dependências/imports |
| AC-15 | Dados dois usuários/lojas, cada resumo contém só produtos e ações da loja autorizada; sem loja não exibir global; alteração não autorizada de ID é rejeitada no servidor | Contrato e testes de integração DS_Backend exigidos por D5; Mobile com respostas controladas |
| AC-16 | Dado sucesso persistido, reabrir offline com a mesma identidade autorizada mostra os mesmos dados/data; outra identidade/loja não consegue lê-los | Teste de persistência/recriação e de isolamento de identidade; instrumentado de reabertura |
| AC-17 | Dado cache da data atual em America/Sao_Paulo, mostrar como válido; em data posterior nesse fuso exibir aviso aprovado com data da última atualização; virada de data não dispara rede e mudança de timezone do dispositivo não altera o resultado | Unitários com relógio fixo nas fronteiras de meia-noite em São Paulo e em dispositivos com fusos diferentes; ViewModel e instrumentado do aviso |
| AC-18 | Dada resposta inválida/falha/cancelamento, não alterar data do cache; dado sucesso, substituir conjunto e timestamp atomicamente | Unitários use case e integração do armazenamento |
| AC-19 | Dados cache corrompido/versão incompatível/falha de escrita, manter fluxo remoto operacional, sem falsos zeros ou indicação falsa de persistência | Testes de armazenamento e ViewModel |
| AC-20 | Dada saída/expiração ou troca de loja com gravação pendente, invalidar acesso imediatamente e impedir que commit atrasado repovoe cache | Teste concorrente determinístico de invalidação/commit e navegação |
| AC-21 | Dado 429 com Retry-After em segundos/HTTP-date, bloquear tentativas até o prazo; vencimento não busca automaticamente; inválido/ausente permite retry manual | Unitários com relógio fixo e teste HTTP; entrada/retorno à Home durante bloqueio |
| AC-22 | Dada ausência de uma única loja ativa confirmada pelo servidor, mostrar a mensagem aprovada e cards sem números, sem seletor/global/cache anterior; falha de rede não produz falso estado sem loja | Testes de contrato D5 e unitários use case/ViewModel; instrumentado do estado sem loja |

## Decisões e pontos em aberto

| Item | Status | Fonte / responsável |
| --- | --- | --- |
| Endpoint, campos e semântica originais | Resolvido | Contrato e implementação DS_Backend, seção acima |
| Config Core em XML e autenticação pelo cliente existente | Resolvido | Solicitação, config e código atual; sem endereços no documento |
| Não reaproveitar feature/cache cancelados como decisão vigente | Resolvido | QUIS-128, anotação de cancelamento |
| D1 — somar ruptura/excesso, mutuamente exclusivos por produto na mesma loja | Resolvido | Solicitante, 16/10/2026; garantia deve integrar contrato/testes backend |
| D2 — exigir resumo da loja do usuário, resolver backend antes de implementar | Resolvido (produto) | Solicitante, 16/10/2026; entrega do contrato continua pendente em D5 |
| D3 — buscar ao entrar/retry e persistir por usuário; mesma data válida, datas posteriores com aviso e data do último sucesso | Resolvido | Solicitante, 16/10/2026; fuso/copy fechados em D6 |
| D4 — mensagens 403/429/contrato e política Retry-After propostas | Resolvido | Aprovadas pelo solicitante, 16/10/2026 |
| D5 — contrato autorizado por loja e identidade durável offline | Open (técnico) | Responsáveis Core/Auth; requisitos abaixo. Não há aprovação de contrato inexistente |
| D5-P — elegibilidade exige uma única loja ativa; sem loja mostrar mensagem aprovada, sem global/seletor | Resolvido | Solicitante, 16/10/2026; contrato técnico deve representar esse estado |
| D6 — fuso America/Sao_Paulo e copy de cache desatualizado/falha de persistência | Resolvido | Solicitante, 16/10/2026; ambas as mensagens aprovadas |

### D5 — contrato necessário no backend (ainda não existente)

`GET /profile` já retorna `id`, `store_id` interno e `store_code` ERP pelo `sub`
autenticado. Esses IDs não são intercambiáveis. O contrato atual permite store_id
nulo; FUNCIONARIO possui uma loja ativa, GERENTE_REGIONAL possui região, e GERENTE/
ADMIN não têm loja obrigatória. A decisão aprovada exige exatamente uma loja ativa
para qualquer usuário elegível, sem ampliar o cadastro de perfis nesta entrega.
Nenhuma loja pode ser inferida de nome ou posição. Usuário sem atribuição elegível
vê “Nenhuma loja vinculada ao seu usuário.”; isso não é expiração de sessão.

Antes de implementar, registrar no DS_Backend:

1. Método/rota definitiva, modo de determinar a loja (preferência técnica: servidor
   resolve a atribuição pelo usuário autenticado) e identificação inequívoca do escopo
   na resposta. Não inventar query parameter `store_id` no Mobile.
2. Representação técnica definitiva (status/body) do estado sem uma única loja ativa
   e da atribuição removida/modificada, conforme comportamento aprovado acima.
   Não usar região inteira ou primeira filial como substituto. A confirmação de perda
   de atribuição invalida o cache do escopo anterior; indisponibilidade não confirma perda.
3. Isolamento de produtos **e ações** por loja, garantia de ruptura/excesso sem
   interseção, e testes com o mesmo produto em lojas distintas.
4. Identidade Core durável vinculada à sessão Auth para reabertura offline; decidir
   onde obter/persistir o vínculo autorizado e como invalidá-lo na troca de conta/loja.
   Apenas consultar `/profile` online não resolve a reabertura offline. Não decodificar
   JWT nem reutilizar identidade Firebase como solução implícita.
5. Payload/metadados e códigos de erro definitivos, documentação e testes do contrato
   por loja disponíveis. A API global atual não satisfaz AC-15.

### D6 — validade e mensagens aprovadas

- Usar o instante do recebimento bem-sucedido e um relógio injetável; converter tanto
  esse instante quanto o instante atual para `America/Sao_Paulo` ao comparar datas.
  Persistir o instante do último sucesso. Mesmo dia é válido; dia atual posterior
  indica possível desatualização. Alterar o timezone do dispositivo não muda o resultado.
  Ajuste manual do relógio não atualiza o instante salvo; se a data atual retroceder
  para antes da data salva, tratar como possível desatualização, pois não é a mesma data.
- Aviso: “Dados possivelmente desatualizados. Última atualização: {data}.”
  Formatar a data no locale do app; não ocultar números antigos.
- Falha de gravação: “Indicadores atualizados, mas não foi possível salvá-los para
  acesso offline.” Manter os dados remotos em memória.

**Gate de implementação:** resolver o contrato técnico D5, incorporar contrato e mecanismos
de identidade/cache aos critérios e só então alterar status para Ready for
implementation. As decisões de produto D1–D4, D5-P e D6 estão fechadas. Nenhum
código da integração deve começar enquanto o contrato por loja
e o vínculo offline permanecerem indefinidos.

## Plano de verificação e resultado

Na implementação: escrever testes de comportamento primeiro (Red), implementar o
mínimo para passarem (Green) e refatorar preservando as fronteiras (Refactor).
Executar `.\gradlew.bat testDebugUnitTest spotlessCheck detekt lintDebug assembleRelease`.
Executar `connectedDebugAndroidTest` com dispositivo/emulador disponível; registrar
limitação se ausente. O gate CI continua 80% linhas/70% branches com cobertura
combinada unitária/instrumentada; relatório unitário local não comprova esse gate.
Smoke manual em desenvolvimento: login, resumo, comparação com resposta Core autorizada
e retry após indisponibilidade, sem registrar credenciais, payload pessoal ou URL privada.

Entrega atual: documentação apenas; nenhuma alteração de comportamento ou implementação.
Completar no PR de implementação: critérios entregues, evidência executada e diferenças aprovadas.
