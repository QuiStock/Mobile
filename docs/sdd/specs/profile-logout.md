# Logout na tela de perfil via DS_Auth

**Status:** Complete — implementação entregue; verificações de execução pendentes
**Issue:** none  
**Product decision owner:** Solicitante, conforme decisões registradas nesta conversa

> Atualização de 2026-10-20: logout agora obtém e envia o par CSRF exigido pelo DS_Auth. A sequência foi aprovada e implementada conforme [CSRF no logout](profile-logout-csrf.md). Testes e validações de execução continuam pendentes por instrução do solicitante.

## Problem and goal

O perfil apresenta “Sair da conta” (`profile_sign_out_button`), mas `ProfileFragment` não trata esse clique. Permitir encerrar a sessão do aplicativo e solicitar a revogação remota conforme DS_Auth, sem permitir que respostas antigas restaurem a sessão encerrada.

Fonte do pedido: solicitante em 16/10/2026, com a condição explícita de que esta spec só pode ser implementada quando não houver mais pontos em aberto. Após resolução de todas as decisões, o solicitante autorizou implementação e commits em inglês, sem testes ao final nem verificações dependentes de Gradle.

### Fontes consultadas

- [DS_Auth README](../../../../DS_Auth/README.md), seções “Rotas” e “Java 25 e roteamento por ambiente”.
- [DS_Auth authentication-api-plan](../../../../DS_Auth/docs/authentication-api-plan.md), seção 4, “Logout”, seção 8, “Mobile”, item 6, e critérios Mobile da seção 10.
- [Arquitetura Mobile](../../../README.md), [spec de cookies](auth-cookie-jar-migration.md) e [spec do perfil](profile-screen.md).
- Código atual: `ProfileFragment`, `SessionUseCase`, `AuthRepository`, `AuthApi`, `AuthCookieCustody`, `EncryptedAuthCookieStore` e `nav_graph.xml`.

Documentação local consultada em 16/10/2026. Exemplos históricos de integração do plano DS_Auth não substituem o README atual nem o código Mobile. Não foram executadas chamadas ao serviço.

## Scope

**In scope:** ação de logout no perfil principal em debug/release; tentativa de revogação da família atual em DS_Auth; encerramento local inclusive offline; estados da ação, falhas, concorrência e navegação conforme decisões abaixo.

**Out of scope:** logout de todas as sessões/dispositivos, revogação imediata de JWT no Core, alteração de conta ou perfil, perfil de funcionário, demo, mudanças no backend e reformulação do login/refresh. A demo mantém sua navegação simulada. Nenhuma migração de formato de dados é prevista.

## Expected behavior

### Contrato confirmado

1. DS_Auth recebe `POST /logout`, com `refresh_token`, cookie `AUTH-XSRF-TOKEN` e token mascarado de `GET /csrf` no header `X-CSRF-TOKEN`, sem corpo JSON. Não enviar access cookie nem Bearer nessa operação, preservando a política de exposição mínima da spec de cookies.
2. `204 No Content` confirma sucesso; token desconhecido ou já revogado também retorna `204`. A revogação afeta a família do refresh enviado, sem encerrar famílias independentes de outros logins.
3. O servidor expira `access_token` e `refresh_token` com valor vazio e `Max-Age=0`, preservando Path, Domain, SameSite, Secure e HttpOnly originais. Logout não deve usar a validação de sucesso de login/refresh que exige um novo par válido.
4. O app tenta a chamada e limpa a sessão local inclusive offline. Falha de rede ou HTTP não permite conservar credenciais locais para manter a sessão. Sem confirmação remota, não afirmar que a família foi revogada no servidor.
5. Access já emitido pode continuar válido no DS_Backend até expirar. O cliente deixa de utilizá-lo ao encerrar a sessão; não promete invalidação imediata no servidor.

### Fluxo aprovado

- A ação parte do controle existente “Sair da conta” e abre confirmação com título “Sair da conta?”, mensagem “Deseja encerrar sua sessão neste aparelho?” e botões “Cancelar” e “Sair”. Cancelar ou fechar o diálogo mantém a sessão sem chamar logout.
- Na confirmação, posicionar o foco inicial em “Cancelar”. Após confirmar, apresentar e anunciar “Saindo…”, bloquear cliques repetidos e oferecer rótulos/estados acessíveis. Se a revogação local concluir, mas a remota falhar, exibir sobre Login um diálogo com título “Saída do aparelho concluída”, mensagem “Você saiu deste aparelho, mas não foi possível confirmar o encerramento no servidor.” e botão “Entendi”; anunciar a mensagem ao TalkBack e levar o foco ao título. Não usar essa mensagem quando a própria revogação local falhar.
- Fazer uma única tentativa remota, sem retry automático ou posterior, inclusive em eventual 429, com timeout HTTP total de 10 segundos compartilhado entre GET CSRF e POST logout e cancelamento efetivo da chamada no limite. Não guardar refresh para tentar depois. Falha remota mantém a limpeza local e apresenta o aviso aprovado.
- Na falha local descrita abaixo, levar o foco à mensagem de erro. Descartar estados e caches em memória vinculados à sessão encerrada, limpar a conversa e cancelar trabalho da sessão. Remover também o refresh legado e somente a chave do identificador de usuário em SharedPreferences, verificando remoção durável. Preservar demais preferências, dados de negócio e dados de Analytics/Crashlytics. Falha nessas remoções também usa a recuperação local aprovada. A inspeção e o escopo aprovado estão registrados em P-05.
- Impedir operações duplicadas durante um logout e impedir novos usos da sessão encerrada. Respostas de login, refresh e Core anteriores não podem reinstalar cookies, persistir credenciais, renderizar dados autenticados ou navegar para área autenticada após o encerramento.
- Ao confirmar, bloquear novos usos/renovações, capturar o refresh elegível exclusivamente em data, avançar a geração e iniciar a limpeza local sem esperar a rede. Não esperar refresh concorrente; descartar seus commits tardios. Usar a captura privada apenas na tentativa remota, sem publicar cookies recebidos nem afetar um novo login. Credenciais nunca chegam ao ViewModel. Manter a operação fora do ciclo de vida da View para sobreviver à recriação. Voltar não cancela a operação confirmada. Morte do processo pode interromper chamada e limpeza; não prometer conclusão nesse caso nem criar fila persistente de retry.
- Remover access da memória e revogar a persistência criptografada do refresh usando o mecanismo existente. Em falha de destruição de chave, tentar também remover o registro. Se ambos falharem, bloquear o acesso às telas autenticadas e mostrar “Não foi possível concluir a saída neste aparelho. Tente novamente.” com “Tentar novamente”, repetindo apenas a limpeza local, sem alegar sucesso. Não prometer que a sessão não voltará após reiniciar o processo.
- Após encerramento local concluído e resultado remoto ou timeout, abrir Login e limpar a pilha para impedir retorno a conteúdo autenticado pelo botão Voltar ou por respostas tardias. A rota necessária deve ser implementada; a spec não presume uma rota já existente de perfil para login.
- Sem refresh inicialmente elegível, tentar logout sem cookie e sem GET CSRF, sem enviar credencial expirada. Aceitar 204 mesmo sem cookies de expiração; registrar diagnóstico sanitizado da divergência contratual, sem afirmar revogação de família não identificável. Demais status, falhas de transporte e timeout seguem o aviso remoto aprovado e não restauram sessão.
- Usar `/logout` na origem Auth configurada separadamente da Core. O solicitante confirmou ambiente remoto sem prefixo. Preservar escopo real dos cookies e permitir refresh somente em `/refresh` e `/logout`, sem ampliar Domain/Path nem enviar cookies fora da origem. URLs base separadas não tornam rotas root-relative compatíveis com prefixos; suporte futuro a prefixos exige ajustar também as rotas e a política de cookies.
- O controle e seus estados devem ser utilizáveis por TalkBack e teclado, com indicação acessível de progresso/erro conforme O-02.
- Diagnóstico aprovado pelo solicitante em 16/10/2026: logout registra início e resposta HTTP em debug, com `operation=auth_logout`, e falhas de transporte com categoria sanitizada e duração, seguindo login/refresh. Não registrar URLs, corpos, cookies, tokens, identidade ou mensagens/stack traces de exceções. Esses eventos descrevem a chamada remota, não comprovam limpeza local.

## Acceptance criteria and evidence

Critérios planejados com decisões concluídas. Aplicar Red–Green–Refactor na futura implementação.

| ID | Observable given / when / then | Planned test or check |
| --- | --- | --- |
| AC-01 | Dado perfil autenticado, quando toca em “Sair da conta”, então pede confirmação; cancelar não inicia logout e mantém a sessão; confirmar inicia um único logout, apresenta “Saindo…” e bloqueia cliques repetidos, inclusive após recriação da tela. | Teste unitário do ViewModel e instrumentado de confirmação/cancelamento, clique repetido e recriação. |
| AC-02 | Dado refresh elegível, quando o logout remoto é enviado, então usa POST na rota Auth aprovada, sem JSON, com refresh cookie e par CSRF Auth isolado e sem credenciais em outras origens/rotas ou redirects. | Testes de repositório e custódia com servidor HTTP de teste; validar método, rota, corpo e headers. |
| AC-03 | Dado retorno 204, com ou sem cookies de expiração, inclusive para token desconhecido/já revogado, quando o logout termina, então não exige novo par de cookies e encerra a sessão local; ausência dos cookies gera apenas diagnóstico sanitizado. | Testes de repositório/use case com fixtures contratuais; smoke integrado para comprovar idempotência e revogação da família. |
| AC-04 | Dado modo offline, timeout, 5xx, resposta inesperada ou eventual 429, quando a tentativa falha, então a limpeza local ocorre e a UI segue O-02/O-04 sem afirmar revogação remota. | Testes de repositório, use case e ViewModel com falhas injetadas. |
| AC-05 | Dado ausência ou expiração de refresh, quando logout é solicitado, então tenta sem cookie, aceita 204 sem afirmar revogação de família não identificável e encerra a sessão local. | Testes de use case/repositório e smoke contratual com DS_Auth. |
| AC-06 | Dado logout com revogação local concluída, quando o processo é reiniciado, então a sessão anterior não é restaurada; quando chave e remoção falham, então apresenta a recuperação aprovada em O-05 sem alegar revogação durável. | Testes unitários de falhas parciais/totais e instrumentado de armazenamento/reinício. |
| AC-07 | Dado login, refresh ou Core em andamento, quando logout ocorre e chegam respostas antigas, então elas não recuperam autenticação nem exibem dados da sessão encerrada; resposta tardia de logout também não limpa um novo login. | Testes determinísticos de concorrência por geração no domínio/custódia e testes de ViewModel. |
| AC-08 | Dado encerramento local concluído, quando Login aparece e o usuário pressiona Voltar, então conteúdo autenticado anterior não reaparece. | Teste instrumentado do Navigation e da pilha. |
| AC-09 | Dada a ação e seus estados, quando usados com TalkBack/teclado, então rótulo, progresso, erro e foco seguem O-02, sem expor dados sensíveis. | Teste instrumentado de semântica e verificação manual com TalkBack, pois anúncio/foco exigem dispositivo. |
| AC-10 | Dado ambiente de integração aprovado, quando login → logout → nova tentativa de refresh da família encerrada ocorre, então logout retorna 204 e a família não renova; outro login independente permanece válido. | Smoke integrado controlado com dados de teste, sem registrar tokens; necessário porque mocks não comprovam revogação real. |
| AC-11 | Dada chamada remota bloqueada, quando decorrem 10 segundos totais de GET CSRF + POST logout, então cancela a chamada sem retry e navega para Login se a limpeza local concluiu; recriação da View não duplica nem cancela a operação. | Teste de transporte com timeout e teste instrumentado de recriação. |
| AC-12 | Dados refresh legado, identificador persistido e conversa em memória, quando logout conclui, então são removidos com confirmação durável onde aplicável; preferências do aparelho e dados de negócio permanecem. Falha de remoção apresenta recuperação local. | Testes de persistência com falhas injetadas e testes de estados da sessão/chatbot. |
| AC-13 | Dada tentativa remota de logout, quando começa, recebe resposta ou falha no transporte, então registra os eventos sanitizados com operation=auth_logout, status quando disponível e duração na falha, sem dados sensíveis. | `AuthTransportDiagnosticsTests`: início/resposta 204 e falha DNS, incluindo campos permitidos; escritos, não executados. |

## Technical impact

- `presentation/profile`: conectar o controle via ViewBinding e ViewModel, seguindo os padrões existentes; recursos para estados aprovados e navegação. Views não acessam infraestrutura.
- `domain`: estender a operação de sessão/porta existente conforme necessidade concreta, mantendo o domínio Kotlin puro e a proteção por geração. Não confundir expiração automática com logout voluntário.
- `data/remote/auth`: adicionar operação remota e permitir refresh cookie na rota de logout aprovada. Antes desta entrega, `AuthApi` só declarava login/refresh e `StagedAuthCookieJar` só autorizava envio em `/refresh`.
- `data/secrets`: reutilizar a revogação existente de chave dedicada e registro, sem afetar outras chaves. Nenhuma credencial em logs, eventos, testes com dados reais ou modelos de UI.
- Koin e grafo de navegação: ajustar somente o necessário. Manter clientes Core/chatbot isolados, sem redirects ou retries automáticos de conexão.
- Ambiente: validação futura com serviço de teste e configuração aprovada por ambiente; não registrar URLs privadas ou segredos. Remotamente altera a família de refresh no MongoDB via DS_Auth; localmente altera apenas a sessão e sua persistência. Não excluir dados de negócio sem decisão explícita em O-05.
- Verificação futura: `testDebugUnitTest`, `spotlessCheck`, `detekt`, `lintDebug`, `assembleRelease`; `connectedDebugAndroidTest` com dispositivo. O gate combinado continua 80% linhas/70% branches; relatório unitário local não comprova esse gate.

## Decisions and open questions

**Bloqueio obrigatório:** não iniciar nenhuma implementação desta spec enquanto existir qualquer item Open nela, inclusive novos itens descobertos. Cada resolução deve registrar decisão, fonte/responsável e data, atualizar comportamento e critérios afetados e eliminar ambiguidades. Só então mudar para `Ready for implementation`. Não herdar a autorização excepcional de implementação com deployment pendente da spec de cookies. Decisões de outras funcionalidades do perfil não precisam ser resolvidas, desde que não afetem este logout.

| Item | Status | Source or decision owner |
| --- | --- | --- |
| D-01 — Contrato POST /logout, refresh cookie, sem JSON, 204 idempotente, família atual e expiração de cookies. | Decidido | DS_Auth README e plano, seção Logout; consultados em 16/10/2026. |
| D-02 — Tentar revogação remota e limpar localmente inclusive offline; não prometer revogação imediata de access. | Decidido | DS_Auth plano, Logout e integração Mobile, item 6. |
| D-03 — Implementação somente após ausência total de pontos Open nesta spec. | Decidido | Solicitante, 16/10/2026. |
| O-01 — Pedir confirmação antes de sair; após encerramento local concluído, abrir Login e limpar a pilha para impedir retorno às telas autenticadas. | Decidido | Solicitante, diálogo interativo em 16/10/2026. |
| O-02 — Textos e apresentação definidos no fluxo: confirmação com foco em “Cancelar”; anúncio de “Saindo…”; aviso remoto com título “Saída do aparelho concluída”, botão “Entendi”, anúncio e foco no título; falha local com foco na mensagem e nova tentativa de limpeza. Eventual 429 segue tentativa remota única e aviso aprovado. | Decidido | Solicitante, diálogos interativos em 16/10/2026. |
| O-03 — P-03 aprovada: tentativa sem cookie quando não elegível, aceitar 204 mesmo sem cookies de expiração e limpar localmente. | Decidido | Solicitante, diálogo interativo em 16/10/2026; análise estática DS_Auth abaixo. |
| O-04 — P-04 aprovada: bloqueio imediato, captura privada, limpeza sem esperar rede, tentativa única com timeout HTTP total de 10 segundos compartilhado entre GET CSRF e POST logout, sem esperar refresh concorrente; ciclo de vida e limites de morte do processo conforme fluxo. | Decidido | Solicitante, diálogo interativo em 16/10/2026. |
| O-05 — P-05 aprovada: remover refresh atual/legado e identificador persistido, limpar conversa/estados e cancelar trabalho da sessão; preservar demais preferências/dados; falhas usam recuperação local. | Decidido | Solicitante, diálogo interativo em 16/10/2026, após inspeção de persistência. |
| O-06 — Ambiente remoto sem prefixo confirmado; usar /logout na origem Auth separada da Core, mantendo escopo/isolamento de cookies. Compatibilidade real dos atributos será verificada na implementação com testes e smoke; não ampliar escopo para contornar falhas. | Decidido | Declaração do solicitante em 16/10/2026; rotas root-relative verificadas no código Mobile. |

## Implementation outcome

### Análise preliminar e propostas — 16/10/2026

P-03, P-04 e P-05 aprovadas pelo solicitante em 16/10/2026; O-06 resolvido por sua confirmação de ambiente sem prefixo. O registro abaixo preserva a análise que fundamentou as decisões; o fluxo e a tabela acima expressam o escopo final. Inspeção estática; testes DS_Auth existentes foram lidos, não executados.

- **P-03 / O-03:** `AuthController.logout` extrai o cookie ou null e sempre chama `clearCookies` após sucesso do serviço. `RefreshSessionService.logout` retorna sem erro para formato inválido; para formato válido, procura o hash e revoga a família se encontrada, sem verificar expiração. Assim, ausente/malformado resulta em 204 pelo fluxo inspecionado; expirado ainda encontrado revoga a família, e removido pelo TTL resulta em 204 sem revogação adicional, desde que não haja falha de infraestrutura. O teste `logoutIsIdempotentAndRevokesRefreshFamily` cobre token conhecido, repetido e desconhecido, mas não todos esses casos. Proposta: sem refresh elegível, fazer a tentativa sem cookie, respeitando expiry; aceitar 204 como confirmação da operação, sem afirmar revogação de família não identificável. Aceitar 204 mesmo sem Set-Cookie de expiração, pois a limpeza local é independente; registrar apenas diagnóstico sanitizado de divergência contratual. Outros status seguem falha remota, sem impedir limpeza local.
- **P-04 / O-04:** ao confirmar, bloquear novos usos e novas renovações da sessão, capturar o refresh elegível exclusivamente em data, avançar a geração e iniciar imediatamente a revogação local. Não esperar refresh em andamento: descartar seus commits tardios. Enviar uma única chamada com a captura privada anterior, isolada da custódia de qualquer login futuro; respostas de logout nunca instalam cookies nem limpam uma sessão nova. O refresh anterior consumido ainda identifica a família no código DS_Auth inspecionado; isso permite solicitar a revogação sem esperar o sucessor. Proposta de timeout total HTTP: 10 segundos, incluindo conexão e leitura, cancelando efetivamente a chamada ao atingir o limite. Limpeza local não espera a rede. Manter a operação fora do ciclo de vida da View para sobreviver à recriação; aguardar seu resultado limitado antes de concluir a navegação. Não cancelar por Voltar após confirmação; bloquear retorno autenticado. Morte do processo pode interromper a chamada e a própria limpeza: não garantir revogação remota ou conclusão local nesse caso, nem criar fila persistente de retry. Se a limpeza já concluiu, o próximo início não restaura a sessão. Testar deterministicamente as duas ordens de rotação/revogação e respostas tardias antes de considerar o comportamento implementado.
- **P-05 / O-05:** a inspeção encontrou refresh criptografado em DataStore, refresh legado removido por `LegacyAuthCleanup`, `AndroidUserPreferences` com identificador persistido e `ChatbotViewModel` com resposta em memória/cancelamento de conversa. Não foram encontrados Room ou cache de negócio persistido nas buscas do código principal; isso não constitui auditoria de armazenamento de SDKs. Proposta: revogar refresh atual, remover refresh legado e somente a chave do identificador de usuário, além de limpar estados/conversa e cancelar trabalho da sessão. Não apagar todo SharedPreferences, dados de negócio, preferências do aparelho ou dados de Analytics/Crashlytics. Falha ao remover identificador/credencial legado também impede alegar limpeza local concluída e oferece a nova tentativa local aprovada. Confirmar remoção durável; não depender apenas de gravação assíncrona sem resultado. Não alterar o protocolo legado do chatbot nesta entrega.
- **P-06 / O-06:** o Mobile declara login/refresh root-relative e restringe cookies ao caminho de refresh; o README DS_Auth estabelece rotas raiz e prefixos como configuração de deployment. Proposta de escopo: logout usa `/logout` na mesma origem Auth configurada, com refresh somente em `/refresh` e `/logout`, preservando Domain/Path/Secure e isolamento de origem. Esta entrega suportará acesso direto ou gateway que exponha essas rotas na raiz; suporte a prefixo público fica fora de escopo e exige spec própria. Validar em ambiente controlado sem publicar URLs reais. O solicitante confirmou posteriormente que o ambiente remoto não possui prefixo; essa declaração resolve a escolha de roteamento desta entrega. A verificação HTTP real permanece como evidência da implementação, não como decisão de produto pendente.

### Entrega da implementação — 16/10/2026

Todas as decisões foram concluídas antes de implementar. Entregues operação de logout com captura privada e tentativa única, timeout HTTP total de 10 segundos compartilhado entre GET CSRF e POST logout, limpeza local independente da rede, bloqueio por geração, recuperação local, diálogos acessíveis, navegação para Login com limpeza da pilha e limpeza da identidade/conversa legadas. O transporte também bloqueia follow-ups HTTP de logout, inclusive eventual 503 com Retry-After: 0, sem alterar o tratamento das outras operações.

Os testes foram escritos, mas não executados. A restrição explícita do solicitante impediu executar a fase Red/Green e as verificações via Gradle; não há evidência de compilação, aprovação de testes, cobertura ou gates. Nenhum gate foi alterado. Revisão estática, `git diff --check` e leitura XML dos recursos foram as verificações realizadas.

| Critérios | Cobertura adicionada ou referência | Estado da evidência |
| --- | --- | --- |
| AC-01, AC-08 | `ProfileLogoutTests` (instrumentado), `SessionLogoutNavigationTests` | Escritos, não executados; cancelamento, recriação, aviso remoto e pilha. |
| AC-02, AC-03, AC-04, AC-05, AC-11 | `LogoutTransportTests`, `AuthCookieCustodyTests`, `SessionLogoutTests` | Escritos, não executados; rota/origem, cookies, 204, ausência/expiração, 429/503, timeout, concorrência e limpeza independente. |
| AC-06, AC-12 | `LogoutPersistenceTests` (instrumentado), `SessionLogoutTests`, `ChatbotViewModelTests` | Escritos, não executados; recriação do armazenamento, remoção seletiva, falhas e cancelamento. Reinício real do processo ainda exige verificação em dispositivo. |
| AC-07 | `SessionLogoutTests`, `LogoutTransportTests`, testes existentes `CoreSessionInterceptorTests` | Não executados; respostas antigas de login/refresh/logout e isolamento de credenciais. Concorrência real com DS_Auth ainda exige smoke. |
| AC-09 | Implementação de foco/anúncios em `LogoutDialogs`; roteiro manual com TalkBack/teclado | Não executado; comportamento de acessibilidade requer dispositivo, não comprovado por inspeção estática. |
| AC-10 | Smoke login → logout → refresh recusado e sessão independente preservada | Não executado; exige ambiente DS_Auth e contas de teste, sem registrar credenciais. |

O status Complete descreve a entrega do código, não aprovação das verificações pendentes. Qualquer nova decisão em aberto descoberta deve voltar a bloquear implementação adicional conforme a regra obrigatória acima.

### Manutenção de qualidade — 19/10/2026

Refatoração sem mudança de comportamento para os oito apontamentos do relatório Detekt de 19/10/2026: transições e limpeza de sessão agrupadas em `SessionLifecycle`, diagnóstico de transporte em `AuthTransportDiagnostics`, condições de renovação e navegação simplificadas e retornos de login/chatbot reduzidos. Preservados mutex, geração, cancelamento, ordem de revogação e campos dos logs. Incluída a formatação já presente no workspace em `ProfileFragment`, conforme autorização do solicitante. Não foi criada nova spec porque a alteração é interna e preserva os critérios existentes. Nenhuma regra, limite ou exclusão de qualidade foi alterada. Verificação por inspeção do diff e `git diff --check`; testes, Gradle e regeneração do relatório não executados conforme restrição vigente.

Falhas da preparação CSRF impedem POST e seguem AC-04. Se o refresh expirar durante GET, não enviar POST e tratar como remoto não confirmado. AC-02/05/11 são complementados pelos critérios da extensão CSRF aprovada.
