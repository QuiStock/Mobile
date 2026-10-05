# QUIS-128 — Novo fluxo de login: Client-Side

**Status:** Implemented and validated — etapa Client-Side com fontes simuladas
**Issue:** [QUIS-128 — Migrar o fluxo de login via Firebase para o novo fluxo via API de autenticação](https://quistock.atlassian.net/browse/QUIS-128)
**Product decision owner:** Solicitante, nesta conversa

## Problem and goal

Concluir o comportamento do aplicativo para o novo fluxo de autenticação antes de a API real estar disponível. O QUIS-128 está em desenvolvimento e não possui descrição nem comentários; seu título estabelece a migração. O card pai, [QUIS-49](https://quistock.atlassian.net/browse/QUIS-49), ainda descreve Firebase. Para esta etapa, prevalecem as regras informadas pelo solicitante em 01/10/2026.

No início desta etapa, o código continha `AuthRepository.login/refresh`, `AuthTokens`, resultados de autenticação e `LoginUseCase`, que salvava os tokens em `SecretStorage`. Entretanto, `LoginViewModel` e `LoginFragment` ainda usavam o fluxo legado; o Koin registrava `LegacyLoginUseCase`, sem uma implementação do novo `AuthRepository`. Não havia tratamento de Bearer/401/refresh no cliente HTTP.

## Scope

**In scope:** ligar a tela ao novo login; armazenar e utilizar tokens sem antecipar expiração; autenticar operações da API Core; tratar 401 com refresh e recuperação de sessão; retornar ao login quando houver rejeição definitiva da sessão; configurar DI e fontes simuladas para validar os comportamentos sem API real. O tratamento de 401 é responsabilidade Client-Side e pertence a esta etapa, com transporte controlado em testes.

**Out of scope:** implementar ou consumir a API real de autenticação/Core; inventar endpoints, payloads, códigos de erro ou contrato de rotação; validar assinatura ou claims JWT no aplicativo; agendar refresh por expiração; alterar cadastro ou indicadores; reformular a API ou o comportamento do chatbot; remover Firebase de funcionalidades não autorizadas. A sinalização de depreciação do fluxo antigo do chatbot em nível Warning pertence a esta etapa. A origem Core deve ser identificada por configuração explícita, sem supor que toda integração interna pertence a ela.

## Expected behavior

### Login e armazenamento

- O login continua recebendo email e senha pela tela existente. Enquanto autentica, indica carregamento e impede submissões simultâneas. Sucesso só libera a navegação para a Home após armazenar os tokens; falhas deixam a pessoa no login com erro apropriado e permitem nova tentativa.
- A autenticação fornece um access token JWT e um refresh token opaco. UUID aleatório é uma possibilidade, não um formato obrigatório: o aplicativo não exige UUID nem interpreta seu conteúdo.
- Os tokens são armazenados de forma naive quanto à expiração: o aplicativo não interpreta `exp`, calcula validade, usa timers nem faz renovação preventiva por prazo. Isso não significa armazenar segredos em texto aberto.
- Hoje `Secrets` define access token em memória e refresh token persistente, criptografado com Android Keystore e DataStore. Ao reabrir o aplicativo, ele deve usar o refresh token persistido para recuperar automaticamente a sessão, sem pedir email e senha novamente. O sucesso armazena os tokens retornados e permite acesso à área autenticada. Esse refresh de recuperação decorre da reabertura do app e não de cálculo de expiração. Se essa recuperação falhar por rede, timeout ou 5xx, o aplicativo abre a Home com os dados cacheados disponíveis e o aviso correspondente, preservando o refresh token para nova tentativa. A ausência de access token após perda do processo não deve converter uma falha transitória em sessão expirada. Operações remotas continuam sujeitas à recuperação da autenticação; abrir a Home não significa que uma operação remota foi concluída.
- Ao abrir o aplicativo sem refresh token armazenado, ir diretamente para a tela de login, sem tentar refresh e sem mostrar aviso de sessão expirada. Essa ausência não equivale a rejeição de uma sessão existente.
- Se o armazenamento local dos tokens falhar no login ou na recuperação, interromper esse fluxo e impedir acesso remoto com uma sessão parcialmente salva. Informar “Ocorreu um erro inesperado. Tente novamente. Se o erro persistir, entre em contato com o suporte.”, sem mencionar armazenamento ou detalhes técnicos da sessão. A falha local não deve ser apresentada como expiração. Não liberar o sucesso do login antes de concluir o armazenamento; a implementação deve evitar reutilização do par parcial de tokens.
- Se o aplicativo não conseguir ler o refresh token armazenado na abertura, mostrar a tela de login com “Ocorreu um erro inesperado. Tente novamente. Se o erro persistir, entre em contato com o suporte.” e permitir nova autenticação. Não tentar refresh com dados ilegíveis, não tratar falha de leitura como ausência normal de token nem informar sessão expirada.
- O novo resultado de login fornece tokens, não um usuário. Não extrair identidade do JWT nem fabricar UserID para manter o chatbot antigo. A API do chatbot será reformulada; considerar seu fluxo antigo depreciado em nível Warning. O novo login não precisa reproduzir a gravação de UserID do Firebase para esse fluxo legado.

### API Core e refresh

- Toda operação remota dirigida à API Core (a mesma origem dos big numbers) usa `Authorization: Bearer <access token>`. Não colocar tokens em URLs, logs, mensagens de erro ou relatórios; não enviar esse header a origens alheias à Core.
- O primeiro 401 de uma operação Core aciona uma tentativa de refresh na autenticação, usando o refresh token armazenado. O fluxo de refresh não deve recursivamente acionar a si próprio.
- Há no máximo um refresh em andamento por sessão. A primeira operação inicia a renovação; operações concorrentes aguardam o mesmo resultado. Em caso de sucesso, cada operação repete sua própria requisição uma única vez com o novo access token. Um primeiro 401 atrasado de uma requisição enviada com token antigo reutiliza o token já renovado, sem iniciar outro refresh. O limite de uma repetição e o encerramento por segundo 401 continuam valendo para cada operação.
- Falhas transitórias do refresh compartilhado seguem o tratamento de cada operação principal. Rejeição definitiva encerra a sessão uma única vez, sem multiplicar navegações ou avisos. Se houver novo login enquanto um refresh está pendente, a resposta da sessão anterior não pode sobrescrever os tokens nem revogar a sessão nova; operações da sessão anterior não devem ser repetidas sob a identidade da nova sessão.
- Após refresh bem-sucedido, os tokens retornados são armazenados e a operação original é repetida automaticamente uma única vez, usando o novo access token. Se essa repetição retornar 401, o aplicativo encerra a sessão e redireciona ao login com o mesmo aviso de sessão expirada, sem outro refresh ou retry nessa operação. Esse limite evita um ciclo infinito quando o refresh token é válido, mas o access token recebido é inválido, inclusive em caso de possível corrupção da sessão. Um novo login cria uma nova sessão; não se presume que o JWT ou a sessão real contenham email, pois o contrato ainda não foi definido.
- O refresh conta como parte da operação principal: suas falhas são tratadas como falhas dessa operação. Apenas sessão expirada/rejeição definitiva da autenticação retorna ao login com informação de expiração. Ao encerrar a sessão por expiração, apagar access token, refresh token e os dados cacheados vinculados ao usuário, além de limpar a pilha de navegação das telas protegidas. O próximo login, inclusive com outra conta, não pode visualizar dados cacheados da sessão anterior. Essa limpeza não ocorre por falhas transitórias de rede, timeout ou 5xx, que preservam a sessão e o cache. O botão Voltar não permite retornar a essas telas após o redirecionamento ao login. Na tela de login, exibir “Sua sessão expirou. Faça login novamente.” como texto visível, sem desaparecimento por tempo. O aviso permanece durante o preenchimento de email e senha e some quando a pessoa toca em “Entrar” e inicia uma nova tentativa de autenticação. Não persistir esse aviso para exibi-lo após fechar e reabrir o aplicativo.
- Falha de internet no refresh preserva a sessão, mostra os dados cacheados disponíveis e o aviso “Sem conexão com a internet. Verifique sua conexão e tente novamente.”, com botão explícito “Tentar novamente”. Não fazer retry automático ao retornar a conexão. Sem internet, nenhuma operação remota pode ser concluída; manter acesso ao aplicativo não representa sucesso dessas operações. A nova tentativa só começa por ação da pessoa nesse botão, executando novamente a operação principal e recuperando a autenticação quando necessário. Durante a tentativa, desabilitar o botão para impedir submissões simultâneas.
- Timeout é tratado como internet lenta: exibir “Sua conexão está lenta. Tente novamente.”, preservar a sessão e manter os dados cacheados disponíveis, com botão “Tentar novamente”. Erro 5xx exibe “Ocorreu um erro interno. Tente novamente mais tarde.”, mantém acesso ao aplicativo e os dados cacheados disponíveis, com botão “Tentar novamente”. Nenhuma dessas falhas deve ser convertida em sessão expirada. Oferecer “Tentar novamente” também para timeout e 5xx, sem aguardar ou monitorar automaticamente a disponibilidade da API. Essa regra não altera a repetição automática única da operação original após refresh bem-sucedido.
- Não aplicar refresh para respostas diferentes de 401. Falhas de conectividade e erros funcionais da Core seguem seu tratamento próprio.
- Big numbers usam atualmente uma fonte simulada, sem HTTP. Seus valores de cache não exercitam autenticação; testar a política HTTP com transporte simulado. O fallback de cache não deve impedir o retorno ao login exigido após rejeição definitiva da sessão. Para falhas transitórias de refresh, o cache segue o comportamento da operação principal.

### Validação sem API real

Usar uma implementação simulada de `AuthRepository` e respostas HTTP controladas para login, refresh e operações Core. Fixtures devem usar dados sintéticos, sem credenciais ou tokens reais. Os cenários precisam ser determinísticos e incluir sucesso e falhas. URLs, rotas, campos JSON e mapeamento dos erros reais ficam pendentes da API; rotas de teste não constituem contrato de produção.

## Acceptance criteria and evidence

| ID | Observable given / when / then | Planned test or check |
| --- | --- | --- |
| AC-01 | Dado login bem-sucedido, quando os dois tokens são armazenados, então a tela navega para a Home pelo novo fluxo, sem depender de Firebase Auth. | Unitários de caso de uso/DI/ViewModel; instrumentado de navegação. |
| AC-02 | Dada autenticação em andamento, quando há toques repetidos, então há apenas uma autenticação e a tela apresenta carregamento; falha permite tentar novamente. | Unitário do ViewModel; instrumentado da tela. |
| AC-03 | Dados os tokens recebidos, quando armazenados/utilizados, então não ocorre interpretação de expiração nem exigência de formato UUID para refresh. | Unitários com tokens sintéticos e relógio avançado, sem refresh por prazo. |
| AC-04 | Dada uma operação Core com sessão, quando enviada, então contém exatamente o header Bearer do access token atual; chamadas de outras origens não recebem o token Core. | Testes de transporte HTTP controlado para headers e isolamento de origem. |
| AC-05 | Dada resposta 401 da Core, quando tratada pela primeira vez, então ocorre uma tentativa de refresh com o refresh token armazenado, sem recursão na autenticação. | Testes de transporte e coordenação de sessão com contagem de chamadas. |
| AC-06 | Dado refresh bem-sucedido após o primeiro 401, quando a sessão é atualizada, então os tokens retornados são armazenados antes de repetir automaticamente a operação original uma única vez com o novo access token. | Unitário do refresh e teste HTTP verificando ordem, header atualizado e contagem de chamadas. |
| AC-06A | Dada a repetição da operação original após refresh bem-sucedido, quando ela retorna 401 novamente, então a sessão é encerrada e a pessoa retorna ao login com aviso de expiração, sem novo refresh ou retry nessa operação. | Teste HTTP com sequência Core 401 / refresh sucesso / Core 401 e contagem limitada; unitário de sessão e instrumentado de navegação. |
| AC-07 | Dada rejeição definitiva da sessão no refresh, quando tratada, então a pessoa retorna ao login e vê informação de login expirado, inclusive se a feature tiver cache. | Unitário de sessão/ViewModel; instrumentado de navegação a partir de área protegida. |
| AC-07A | Dada falta de internet durante refresh, quando a operação falha, então a sessão é preservada, dados cacheados disponíveis são exibidos com o aviso “Sem conexão com a internet. Verifique sua conexão e tente novamente.” e é oferecido “Tentar novamente”; o retorno da conexão não dispara chamadas automaticamente. | Unitários de sessão e operação com cache presente/ausente e retorno da conexão sem chamadas; instrumentado do botão. |
| AC-07B | Dado timeout durante refresh, quando a operação falha, então a sessão e os dados cacheados disponíveis são preservados e a pessoa vê “Sua conexão está lenta. Tente novamente.” com botão “Tentar novamente”. | Unitários de sessão/ViewModel; instrumentado ou checagem manual do aviso. |
| AC-07C | Dado erro 5xx durante refresh, quando a operação falha, então a sessão é preservada e a pessoa vê “Ocorreu um erro interno. Tente novamente mais tarde.”, mantendo acesso ao aplicativo, cache disponível e botão “Tentar novamente”. | Unitários de sessão/ViewModel e teste HTTP com 5xx; instrumentado ou checagem manual do aviso. |
| AC-11 | Dadas operações concorrentes com 401 na mesma sessão, quando aguardam renovação, então compartilham um único refresh; no sucesso cada uma repete sua requisição no máximo uma vez com o token atualizado. | Teste concorrente determinístico com barreiras e contagem de chamadas HTTP. |
| AC-12 | Dado token já renovado, quando chega o primeiro 401 de uma requisição enviada com token antigo, então ela reutiliza o token atualizado sem novo refresh, respeitando o limite de uma repetição. | Teste de transporte com respostas deliberadamente atrasadas. |
| AC-13 | Dado refresh compartilhado, quando falha transitoriamente, então cada operação aplica seu tratamento preservando sessão; quando há rejeição definitiva, então ocorre um único encerramento, redirecionamento e aviso. | Unitários concorrentes de sessão/operações e instrumentado de navegação e aviso. |
| AC-14 | Dado novo login concluído durante refresh de uma sessão anterior, quando a resposta antiga chega com sucesso ou rejeição, então ela não sobrescreve nem revoga a sessão nova e nenhuma operação antiga é repetida com a nova identidade. | Unitários determinísticos com resposta de refresh atrasada, sucesso e rejeição, verificando armazenamento e repetição. |
| AC-15 | Dada sessão encerrada por rejeição definitiva do refresh ou segundo 401 após renovação, quando a pessoa retorna ao login, então os dois tokens são apagados e a pilha protegida é limpa; o botão Voltar não retorna às telas protegidas. | Unitário de sessão verificando exclusão dos tokens; instrumentado de navegação e botão Voltar para ambos os motivos de encerramento. |
| AC-16 | Dado redirecionamento ao login por expiração, quando a tela é exibida, então mostra “Sua sessão expirou. Faça login novamente.” sem desaparecer por tempo; preencher os campos mantém o aviso, iniciar autenticação o remove e fechar/reabrir o app não restaura esse aviso. | Unitário de estado do login; instrumentado ou checagem manual para texto, permanência e nova abertura, pois dependem de Views e ciclo de vida. |
| AC-17 | Dada falha transitória de rede, timeout ou 5xx, inclusive na recuperação ao reabrir o app, quando a conexão/API volta, então não ocorre retry automático; ao tocar em “Tentar novamente”, a operação principal é executada novamente com recuperação de autenticação quando necessária e o botão fica desabilitado durante a tentativa. | Unitários de operação/ViewModel para ausência de retry automático, nova tentativa e toques simultâneos; instrumentado do botão. |
| AC-18 | Dado aplicativo aberto sem refresh token armazenado, quando a entrada é resolvida, então a tela de login é exibida diretamente, sem chamada de refresh e sem aviso de sessão expirada. | Unitário de resolução da entrada verificando ausência de refresh; instrumentado da inicialização sem tokens. |
| AC-19 | Dado fluxo antigo do chatbot, quando seus contratos/pontos de entrada legados são usados no código, então a depreciação é sinalizada em nível Warning; o novo login não fabrica UserID para esse fluxo. | Revisão das anotações de depreciação e compilação para confirmar Warning em vez de Error; unitário do novo login verificando que não grava identidade legada. |
| AC-20 | Dada expiração por rejeição definitiva do refresh ou segundo 401, quando a sessão é encerrada, então o cache vinculado ao usuário é apagado e não aparece no próximo login, inclusive com outra conta; falhas transitórias preservam esse cache. | Unitários de encerramento e testes de integração Room para limpeza e novo login; instrumentado da Home sem dados da sessão anterior. |
| AC-20A | Dada operação da sessão encerrada ainda pendente, quando seu resultado chega após a limpeza, então não repovoa o cache nem apresenta dados na nova sessão. | Teste concorrente determinístico com resposta atrasada e verificação do cache/estado exibido. |
| AC-21 | Dada falha ao armazenar tokens no login ou recuperação, quando tratada, então o fluxo não conclui autenticação nem usa uma sessão parcialmente salva em operações remotas e mostra “Ocorreu um erro inesperado. Tente novamente. Se o erro persistir, entre em contato com o suporte.” sem detalhes técnicos. | Unitários com falha em cada gravação, verificando estado e ausência de chamadas remotas com par parcial; instrumentado ou checagem manual da mensagem. |
| AC-22 | Dada falha ao ler o refresh token na abertura, quando tratada, então a tela de login mostra a mensagem genérica de erro inesperado/suporte e permite nova autenticação, sem tentativa de refresh com dados ilegíveis e sem aviso de expiração. | Unitário de resolução da entrada com armazenamento lançando erro; instrumentado ou checagem manual da mensagem e possibilidade de autenticar. |
| AC-08 | Dada resposta Core diferente de 401, quando tratada, então não ocorre refresh de autenticação. | Testes HTTP para sucesso, 403, 5xx e falha de conexão. |
| AC-10A | Dado refresh token persistido e access token perdido com o processo, quando a recuperação na reabertura falha por rede, timeout ou 5xx, então a Home abre com o cache disponível e o aviso correspondente, sem revogar a sessão ou apagar o refresh token. | Unitários de recuperação para as três falhas; instrumentado da entrada na Home com cache e armazenamento persistido. |
| AC-10 | Dado refresh token persistido, quando o aplicativo é reaberto e a recuperação tem sucesso, então a sessão é recuperada automaticamente e a pessoa acessa a área autenticada sem informar email e senha novamente. | Unitário de recuperação e DI; instrumentado de inicialização com armazenamento persistido e refresh simulado. |
| AC-09 | Dada API real indisponível, quando exercitados login e recuperação de sessão, então os cenários podem ser validados por fontes e transporte simulados. | Unitários de DI e integração com servidor HTTP de teste; checagem manual da UI justificada por navegação e acessibilidade. |

## Technical impact

- Preservar `presentation -> domain <- data`, Views/XML/ViewBinding, Koin e a organização atual. Reutilizar modelos, portas e armazenamento existentes onde atendem ao comportamento refinado.
- Prováveis alterações em `presentation/login`, casos de uso de autenticação/sessão, `data/secrets`, configuração HTTP em `app/di/RetrofitModule.kt`, módulos de domínio/apresentação e navegação global. Transporte HTTP permanece em `data`; domínio não recebe tipos Android/Retrofit/Firebase.
- `LoginUseCase` salva refresh e access separadamente; tratar a falha parcial para impedir uso remoto de um par incompleto, sem expor detalhes de armazenamento na UI. Sua presença e seus testes não comprovam conclusão do fluxo.
- Não assumir identidade a partir de tokens. O consumidor identificado de `UserPreferences.getUserId` é `ChatbotViewModel`; o fluxo antigo do chatbot depende desse identificador no request. Sinalizar os pontos de entrada/contratos legados pertinentes com depreciação em nível Warning, sem criar um contrato substituto ou silenciar quality gates. A API do chatbot será reformulada em outra etapa.
- A limpeza de cache vinculado ao usuário na expiração está aprovada. O cache Room de big numbers existente não possui isolamento de identidade estabelecido nesta spec; incluir os dados de sessão correspondentes na limpeza e impedir que resultados atrasados da sessão encerrada repovoem o cache ou sejam exibidos na nova sessão. Não apagar dados não vinculados ao usuário ou preferências por inferência. Sessões Firebase existentes não equivalem automaticamente à nova sessão.
- Planejar Red–Green–Refactor para cada comportamento novo. Na implementação, executar `testDebugUnitTest`, `spotlessCheck`, `detekt`, `lintDebug` e `assembleRelease`; executar instrumentados quando houver dispositivo/emulador. Cobertura local unitária não substitui o gate combinado do CI.

## Decisions and open questions

| Item | Status | Source or decision owner |
| --- | --- | --- |
| API fornece access token JWT e refresh token opaco; UUID é apenas provável. | Decidido | Solicitante, 01/10/2026. |
| Não antecipar expiração; operações Core usam Bearer access token. | Decidido | Solicitante, 01/10/2026. |
| Primeiro 401 Core tenta refresh; somente rejeição definitiva da sessão retorna ao login com informação de expiração. | Decidido, refinado | Solicitante, 01/10/2026. |
| Política é Client-Side e pode ser exercitada sem API real. | Delimitação técnica | Escopo solicitado e responsabilidades do cliente. |
| Refresh integra a operação principal; rede, timeout e 5xx preservam sessão e recebem o tratamento da operação. Rede usa cache e nova tentativa explícita; timeout informa lentidão; 5xx informa erro interno. | Decidido | Solicitante, 01/10/2026. |
| Falhas transitórias oferecem botão “Tentar novamente”; sem retry automático por retorno da internet ou disponibilidade da API. Desabilitar botão durante tentativa. Preservar retry único após refresh bem-sucedido. | Decidido | Solicitante, 01/10/2026; simplificar esta etapa. |
| Após refresh bem-sucedido, repetir automaticamente a operação original uma única vez; segundo 401 encerra sessão e retorna ao login como sessão expirada, sem novo refresh/retry. | Decidido | Solicitante, 01/10/2026; evita ciclos com refresh válido e access inválido. |
| Ao reabrir o aplicativo, recuperar automaticamente a sessão pelo refresh token persistido, sem pedir email e senha novamente. | Decidido | Solicitante, 01/10/2026. |
| Recuperação automática com falha de rede, timeout ou 5xx abre a Home com cache disponível e aviso correspondente; preserva refresh token para nova tentativa mesmo sem access token em memória. | Decidido | Solicitante, 01/10/2026. |
| Refresh único compartilhado por sessão; cada operação repete uma vez no sucesso; primeiro 401 atrasado reutiliza token renovado; falhas transitórias seguem cada operação; rejeição encerra uma vez; resposta antiga não sobrescreve nem revoga novo login. | Decidido | Recomendação aceita pelo solicitante, 01/10/2026. |
| Encerramento por expiração apaga os dois tokens e limpa a pilha protegida, impedindo retorno pelo botão Voltar. | Decidido | Solicitante, 01/10/2026. |
| Aviso “Sua sessão expirou. Faça login novamente.” visível na tela sem prazo, mantido durante preenchimento e removido ao iniciar autenticação; não salvo entre fechamentos do app. | Decidido | Solicitante, 01/10/2026. |
| Ao abrir sem refresh token, ir diretamente ao login sem refresh e sem aviso de expiração. | Decidido | Solicitante, 01/10/2026. |
| Falha de gravação interrompe login/recuperação e impede uso remoto de sessão parcialmente salva; mensagem genérica de erro inesperado com orientação de suporte se persistir. | Decidido | Solicitante, 01/10/2026. |
| Falha ao ler refresh token na abertura leva ao login com a mesma mensagem genérica de erro inesperado/suporte e permite nova autenticação, sem aviso de expiração. | Decidido | Solicitante, 01/10/2026. |
| API do chatbot será reformulada; fluxo antigo depreciado em nível Warning; não fabricar UserID nem exigir que o novo login sustente esse contrato legado. | Decidido | Solicitante, 01/10/2026. |
| Expiração apaga os dados cacheados vinculados ao usuário, impedindo exposição da sessão anterior em próximo login com outra conta; falhas transitórias preservam cache. | Decidido | Solicitante, 01/10/2026. |
| Timeout exibe “Sua conexão está lenta. Tente novamente.”, mantém cache disponível e oferece botão “Tentar novamente”. | Decidido | Solicitante, 01/10/2026. |
| Erro 5xx exibe “Ocorreu um erro interno. Tente novamente mais tarde.”, preserva sessão/cache disponível e oferece botão “Tentar novamente”. | Decidido | Solicitante, 01/10/2026. |
| Falta de internet exibe “Sem conexão com a internet. Verifique sua conexão e tente novamente.”, mantém cache disponível e oferece botão “Tentar novamente”. | Decidido | Solicitante, 01/10/2026. |
| Contrato real de login/refresh, rotação e configuração das origens. | Open — dependência futura de integração | Responsável pela API; não bloqueia simulações das regras decididas. |

## Implementation outcome

Client-side implementation uses `MockAuthRepository`, the existing encrypted `SecretStorage`,
and `SessionUseCase`; it does not introduce an authentication endpoint, payload, JWT parsing,
identity claim, or expiry timer. `CoreHttpClient` is a named Koin binding with an explicit
`CORE_BASE_URL` origin, independent of the legacy chatbot's `BACKEND_BASE_URL`. Redirects are
disabled for this client to prevent forwarding an authenticated operation to another origin.
The mock big numbers source still does not perform HTTP.

A session generation fences token writes, retries, cache writes, and Home rendering. A token
revision also recognizes a completed refresh when a synthetic source returns the same token
string. Refresh I/O runs outside the session mutex so a new login can complete while an older
refresh is pending. Token publication follows both storage writes; partial writes block remote
access and are cleaned up. Expiration clears all cached big numbers rows. Installing a new login
also clears this session cache before publishing tokens, preventing reuse across accounts even
if an earlier cleanup was interrupted. Recovery and transient failures preserve the cache.

The Firebase login adapter, authentication port, legacy login use case, and their superseded
tests were removed. Firebase SDK initialization for unrelated features remains. Legacy chatbot
contracts and entry points are deprecated at Warning level. The missing return in the existing
secret reader was a mechanical compilation fix needed for the token flow; it introduces no
new product behavior and uses this spec's storage failure checks.

### Acceptance evidence mapping

| Criteria | Automated evidence |
| --- | --- |
| AC-01, AC-02, AC-09, AC-19 | `LoginUseCaseTests`, `LoginViewModelTests`, `AppModulesTest`, `LoginFragmentTests`; token-only login and Warning annotations. |
| AC-03, AC-10, AC-10A, AC-18, AC-21, AC-22 | `SessionUseCaseTests`: opaque tokens, persisted-token recovery, no-token entry, unreadable storage, partial writes, and transient recovery results. |
| AC-04, AC-05, AC-06, AC-06A, AC-08 | `CoreSessionInterceptorTests`: isolated origin, replacement Bearer header, stored refreshed tokens, bounded retry, second-401 expiration, non-401 responses. |
| AC-11, AC-12, AC-13, AC-14 | `SessionUseCaseTests` with deferred responses and concurrent operations; `CoreSessionInterceptorTests` with a two-request HTTP barrier. |
| AC-07, AC-15, AC-16, AC-20 | `SessionNavigationTests`, `LoginFragmentTests`, `RoomBigNumbersIntegrationTests`, session expiration and generation checks. |
| AC-07A, AC-07B, AC-07C, AC-17 | Session recovery tests, HTTP transient-failure test, `HomeViewModelTests` for retained counts, warnings, action-only retry, and disabled retry during recovery; `HomeFragmentTests` for all three warning messages, visible cache and disabled retry while loading. |
| AC-20A | `SessionBigNumbersTests` for delayed responses after expiration/new login, plus `HomeViewModelTests` for discarded results from another generation. |

Tests were written before the corresponding implementation where practical. The initial test
execution was blocked by the preexisting compilation errors, so it does not establish a
behavioral Red result. During the completion review, the deterministic regression
`delayedStorageReadFailureCannotReplaceNewLogin` was executed and failed before the fix:
a delayed storage read failure replaced the state of a newer successful login. Restoration
now checks the session generation under the mutex before publishing the local failure.
Final validation results are recorded below.

### Executed validation

Validated on 2026-10-01 on Windows with Java 17 and a connected Android 12 device.

| Check | Result |
| --- | --- |
| `testDebugUnitTest` | Passed: 75 tests, no failures, errors or skipped tests. Includes the storage-read race regression after the fix and refactor. |
| `connectedDebugAndroidTest` | Passed: 26 tests, no failures or skipped tests. Covers login, session navigation, Home warnings/retry, Room cleanup and encrypted storage. |
| `spotlessCheck` | Passed after formatting. |
| `detekt` | Passed with zero findings after source refactoring; no quality rules or thresholds were changed. |
| `lintDebug` | Passed: zero errors and 301 warnings. |
| `assembleRelease` | Passed with the final session implementation; generated the unsigned release APK. |
| `jacocoTestCoverageVerification` | Passed using combined local unit and instrumented execution data: 881/1004 lines (87.75%) and 283/364 branches (77.75%). Required thresholds remain 80% and 70%. |

The final debug validation command was `gradlew.bat --max-workers=2 spotlessCheck detekt
testDebugUnitTest lintDebug connectedDebugAndroidTest jacocoTestCoverageVerification --continue`.
Release assembly was validated separately in the preceding full check execution. The local
combined result does not claim a completed CI run or replace the CI aggregate gate.

The Windows wrapper's empty classpath argument was removed so it can launch the wrapper JAR.
The local Java Unix-domain socket failure required an invocation-only TCP fallback; worker
concurrency was limited during validation. No persistent project JVM settings were changed.

Implementation was recorded in semantic commits for mechanical build/storage fixes, session
lifecycle, Core transport, login migration, navigation, Home/cache, chatbot deprecation, the
final session quality refactor, and documentation. Code and behavioral tests are grouped together.

The real API contract and error mapping remain Open for the integration stage. Authentication
and big numbers still use synthetic sources; HTTP policy was exercised with controlled transport.
