# Migração da autenticação para cookies com persistência seletiva

> Current scope (2026-10-15): the preceding indicator feature was cancelled. Its Home UI, fetch, cache integration and associated tests are removed. Historical references to those components and validation below describe the former stack only. The current cleanup criteria and evidence are recorded in QUIS-128 under "Removal of the cancelled indicator feature". Authentication credential revocation, generation fencing and HTTP recovery remain in scope.

**Status:** Implemented client-side; Android execution and deployment validation pending
**Issue:** none — evolução do fluxo [QUIS-128](QUIS-128-login-client-side.md)
**Product decision owner:** Solicitante; contrato DS_Auth adotado; configuração de deployment pendente

## Problem and goal

Substituir a custódia manual de tokens por cookies, respeitando sua validade e mantendo a recuperação de sessão ao reabrir o aplicativo. Somente refresh persiste ao fechamento do app. Em 13/10/2026, o solicitante definiu o TTL recebido no cookie como fonte da verdade, substituindo o requisito anterior de seis meses. Seguir o contrato DS_Auth: cookies na autenticação e Bearer derivado do access cookie privado nas operações Core.

Estado anterior verificado (QUIS-128): `SessionUseCase` mantém tokens em campos e `SessionSnapshot`, grava ambos via `SecretStorage` e coordena restore/refresh por geração e revisão. `Secrets` configura access em memória e refresh em DataStore criptografado com Android Keystore/AES-GCM. `CoreSessionInterceptor` injeta `Authorization: Bearer`, compartilha refresh via caso de uso e limita a repetição após 401. `AuthRepository` recebe/devolve valores de tokens; sua implementação atual é `MockAuthRepository`. A API real não está integrada. O OkHttp dos testes está na versão 4.12.0; há MockWebServer disponível.

Esta spec substitui as regras de QUIS-128 sobre validade naive e custódia manual. Preserva Bearer no Core, agora derivado do jar em data, e os comportamentos de UI, cache, falhas transitórias, isolamento e concorrência, salvo alterações explícitas abaixo.

## Scope

**In scope:** CookieJar específico da autenticação; validade obtida de `Set-Cookie`; access somente em memória; refresh persistido com metadados e criptografia; envio pelo header `Cookie`; recuperação e renovação; isolamento da Core/chatbot; adaptação dos contratos internos de domínio e DI; migração dos dados locais; testes determinísticos com transporte controlado.

**Out of scope:** inventar endpoints, nomes de cookies, payloads ou códigos da API; interpretar JWT; refresh por timer/background; alterar telas/cadastro/chatbot, exceto mensagem de 429 e erro de plataforma contratados; implementar logout remoto nesta entrega; trocar arquitetura, módulos, gates ou mecanismo criptográfico sem necessidade demonstrada. A integração de produção depende do contrato real; fixtures HTTP não o definem.

## Expected behavior

### Contrato e validade

- Login e refresh recebem `access_token` e `refresh_token` por `Set-Cookie`. Login usa `POST /auth/login` com email, password e platform=mobile; refresh usa `POST /auth/refresh`, sem JSON, com refresh em Cookie. Ambos retornam 200 sem corpo e os dois cookies válidos. Core usa Bearer extraído do access cookie privado, exclusivamente em data.
- TTL recebido é a fonte da verdade. O contrato atual DS_Auth emite 300 segundos para access e 1296000 segundos para refresh, com rotação obrigatória a cada renovação. O cliente usa `Max-Age`/`Expires`, sem criar validade ao carregar do disco nem deduzi-la do JWT; `Max-Age` prevalece. Sucesso sem os dois cookies válidos ou cookie de autenticação sem Max-Age/Expires bloqueia a instalação e apresenta erro inesperado.
- Cookie com `expiresAt <= agora` não pode ser enviado. `Cookie.matches(url)` verifica domínio/caminho/Secure, mas não substitui a verificação de expiração. Usar relógio controlável nos testes e tempo de parede para timestamps HTTP; não usar sleeps reais para simular TTL.
- Atualizar cookies por identidade `(nome, domínio, caminho)`. `Set-Cookie` expirado ou `Max-Age=0` elimina o correspondente também do disco. Ausência de cookie não significa apagá-lo. Aplicar alterações elegíveis recebidas também em respostas de erro, sem presumir que o servidor preserva cookies; respeitar geração, validade e persistência. Isso não transforma erro HTTP em sucesso. Expiração, remoção ou falha local continuam prevalecendo sobre preservação em falhas transitórias.
- Aceitar/persistir apenas cookies de autenticação explicitamente identificados pelo contrato, em origens e escopos aprovados. Não persistir cookies desconhecidos por terem `persistent=true`. Não ampliar Domain/Path nem fabricar metadados.

### Custódia, persistência e recuperação

- O jar é a única fonte de verdade dos cookies ativos. Access nunca é serializado em disco, backups, estado salvo de UI ou cache de domínio. Refresh fica em memória e em armazenamento criptografado, com valor e metadados necessários para reproduzir identidade, expiração e restrições de envio (inclusive hostOnly/Secure/HttpOnly).
- Ao perder o processo, perder access. Ao iniciar, carregar somente refresh válido e permitido; obter novo access por refresh antes de uma operação protegida. Reabrir antes do prazo não estende a validade do refresh.
- Ausência normal de refresh na abertura mantém login sem aviso e sem chamada de refresh. Refresh expirado na abertura não é enviado e mostra o aviso existente de sessão expirada; remove o refresh expirado e limpa o cache vinculado ao usuário. Durante sessão existente, ausência/expiração definitiva impede renovação e encerra a sessão conforme QUIS-128.
- Upgrade apaga a credencial legada sem TTL/escopo e exige novo login; não fabrica validade nem mantém dois stores de autenticação ativos.
- Se a rotação remota consumir o refresh antigo e a gravação do sucessor falhar, bloquear/encerrar a sessão local, exigir novo login e apresentar erro inesperado.
- Expirar access não encerra a sessão se houver refresh válido. Na próxima operação protegida, recuperar access antes de enviá-la; não agendar renovação em background. Na restauração, falhas transitórias preservam refresh válido/cache e seguem a UI de QUIS-128.
- Falhas de leitura/descriptografia/gravação mantêm os resultados locais de QUIS-128: erro inesperado, sem sucesso de login/restauração nem uso remoto de par parcialmente instalado. A publicação em memória segue a persistência bem-sucedida. A integração precisa propagar falhas do jar, sem escondê-las como ausência de cookies ou sucesso HTTP.
- Encerramento definitivo remove cookies de memória/disco e cache do usuário. Se a remoção do disco falhar, bloquear a sessão atual; destruir a chave Keystore exclusiva da autenticação antes de remover o registro. Se a chave não puder ser destruída, tentar remover o registro; falha de ambos bloqueia a sessão e produz erro local, sem prometer persistência durável quando todos os mecanismos de armazenamento falham. Não prometer atomicidade entre DataStore e Room.

- Após iniciar a persistência, concluir a etapa curta de persistência/commit antes de propagar cancelamento cooperativo; publicar somente se a geração continuar corrente. Em falha de gravação, bloquear a sessão e exigir login com erro inesperado. Cancelamento isolado não é erro de armazenamento. Encerramento abrupto do processo não garante conclusão; testar recriação nos limites de commit.

### Renovação, concorrência e isolamento

- Preservar um refresh em andamento por geração, reutilização da revisão já renovada após 401 atrasado, uma repetição da operação após renovação e encerramento por segundo 401. Refresh/login não passam pela recuperação recursiva de operações protegidas.
- Não tratar rede, timeout, 429 ou 5xx como rejeição definitiva. Preservar cache e refresh ainda válido que não tenha sido removido/substituído por Set-Cookie, mantendo retry manual. Para 429, informar amigavelmente que o limite de tentativas foi ultrapassado e pedir para aguardar; usar "Limite de tentativas ultrapassado. Aguarde e tente novamente."; Retry-After válido bloqueia tentativas até o prazo e sua conclusão apenas reabilita o botão. Refresh 401 com invalid_refresh_token é rejeição definitiva. O TTL continua correndo durante indisponibilidade.
- Publicação dos cookies recebidos precisa estar vinculada à geração da requisição. Resposta antiga, inclusive `Set-Cookie` de sucesso, exclusão ou rotação, não pode alterar memória/disco de uma sessão nova. O controle deve ocorrer antes da mutação no jar, e não apenas após `auth.refresh` retornar. CookieJar recebe URL/cookies, não geração: um jar global sem contexto é insuficiente.
- Decisão aprovada: contexto de transporte/jar por geração, com recebimento isolado e commit condicionado à geração corrente. Definir/testar detalhes de sincronização e atomicidade antes de ligar transporte; não depender de ThreadLocal. Login concorrente e operação antiga não podem publicar cookies nem repetir sob identidade de outra conta.
- Escopo de cookie não substitui isolamento de cliente/origem. Manter restrição explícita de scheme/host/port, redirects desabilitados e chatbot independente. Enviar refresh somente nas operações Auth que o exigem (refresh; logout se implementado). Login não envia cookies existentes; Core usa apenas Bearer, sem cookies de autenticação. Aplicar essa política adicional sem ampliar Domain/Path. Origens e atributos por ambiente permanecem Open; exemplos históricos de prefixo não são configuração de produção.
- Revisão deve avançar após renovação confirmada mesmo quando os valores sintéticos forem iguais. Sem expor valores de tokens para detectar mudanças.
- Não incluir cookies, `Set-Cookie`, credenciais ou valores de tokens em logs, exceções, analytics ou relatórios de testes.

## Acceptance criteria and evidence

| ID | Observable given / when / then | Planned test or check |
| --- | --- | --- |
| AC-01 | Dado login com cookies válidos, quando concluído, então refresh é persistido criptografado antes da publicação da sessão e access permanece apenas em memória. | Unitário jar/repository com ordem de commit; instrumentado DataStore/Keystore verificando arquivo e nova instância. |
| AC-02 | Dado cookie recebido, quando o relógio alcança exatamente `expiresAt`, então ele não é enviado; antes do limite permanece elegível. | Unitários com relógio falso, Max-Age/Expires e precedência; MockWebServer com header recebido. |
| AC-03 | Dado refresh persistido, quando o processo é recriado, então access não existe e refresh mantém expiração/escopo originais; restauração obtém novo access sem credenciais. | Unitário round-trip de todos os metadados; instrumentado recriação do armazenamento e sessão. |
| AC-04 | Dado access expirado e refresh válido, quando inicia operação protegida, então há um refresh compartilhado e a operação usa Bearer derivado do novo access cookie, sem timer. | Transporte controlado/relógio falso e teste concorrente; ausência de chamadas durante avanço ocioso. |
| AC-05 | Dada resposta com rotação ou exclusão, quando aplicada, então substitui apenas a identidade correspondente e apaga do disco refresh removido. | Unitários com identidades distintas, vários Set-Cookie e Max-Age=0; round-trip persistente. |
| AC-06 | Dadas chamadas Core, chatbot, outra origem/porta ou redirect, quando executadas, então somente refresh Auth recebe refresh cookie; Core recebe Bearer derivado do access cookie, sem cookies; login/chatbot/destinos não aprovados não recebem credenciais da sessão. | MockWebServer, duas origens/portas, Domain/Path/hostOnly/Secure e redirect; teste DI. |
| AC-07 | Dado primeiro 401, quando refresh sucede, então repete a operação uma vez; segundo 401 encerra sem recursão. | Adaptar `CoreSessionInterceptorTests` para cookies e contagem de chamadas. |
| AC-08 | Dadas operações concorrentes/401 atrasado, quando renovadas, então compartilham um refresh/revisão, inclusive com valores iguais. | Adaptar testes determinísticos de sessão/HTTP com barreiras. |
| AC-09 | Dado novo login durante requisição antiga, quando chegam Set-Cookie antigos de sucesso/remoção ou rejeição, então memória, disco, sessão e cache novos permanecem intactos e não há retry sob nova identidade. | Teste HTTP concorrente que observa callbacks reais do jar, seguido de nova instância do armazenamento; testes de cache por geração. |
| AC-10 | Dado erro de leitura/gravação/criptografia ou cancelamento durante instalação, quando tratado, então não publica sessão parcial nem autoriza operação remota antes do commit; cancelamento cooperativo após início da persistência conclui a etapa curta antes de ser propagado, com publicação condicionada à geração. Falhas locais apresentam erro previsto. | Unitários com falha injetada por etapa, cancelamento e limpeza falhando; instrumentado de mensagem/recriação e teste de chave revogada; falha simultânea segue a limitação documentada. |
| AC-11 | Dado refresh indisponível por rede/timeout/429/5xx sem alterações de cookies, quando falha, então preserva refresh ainda válido/cache, aviso e retry explícito; não há retry ao voltar conexão. | Adaptar `SessionUseCaseTests`, `HomeViewModelTests`, `HomeFragmentTests` e HTTP. |
| AC-12 | Dado refresh ausente/expirado, quando restaura ou tenta renovar, então não o envia nem revive sua validade; ausência na abertura não mostra expiração e sessão existente sem renovação encerra. | Unitário relógio falso e navegação; verificar aviso, remoção do refresh expirado e limpeza do cache na abertura. |
| AC-13 | Dada rejeição definitiva/segundo 401, quando encerra, então limpa cookies e cache, impede retorno às telas protegidas e mantém aviso existente. | Adaptar `SessionNavigationTests`, `SessionBigNumbersTests`, integração Room e persistência; falhas de remoção conforme política de revogação por chave e fallback. |
| AC-14 | Dado armazenamento legado sem TTL/escopo, quando versão nova inicia, então apaga credenciais legadas e exige novo login, sem inventar validade nem manter dois stores ativos. | Teste de upgrade com fixture criptografada sintética; verificar ausência de refresh remoto e exigência de login. |
| AC-15 | Dada arquitetura migrada, quando revisada, então domain/presentation não importam OkHttp/Android/Retrofit nem expõem tokens/cookies; DI resolve clientes e sessão sem ciclos. | Teste de DI e revisão de imports/contratos; checagem manual justificada pela ausência de gate arquitetural específico. |

## Technical impact

### Responsabilidades propostas

| Camada | Responsabilidade e mudanças |
| --- | --- |
| `domain` | `SessionUseCase` mantém estado, geração/revisão, coordenação de refresh e autorização de publicação/limpeza do cache. Remove custódia dos valores e dependência de SecretStorage para autenticação. `AuthRepository` passa a expressar login/refresh/restore/limpeza com resultados sem tokens e contexto opaco de sessão quando necessário. `SessionSnapshot` contém identidade da geração/revisão e disponibilidade sem valores. Exatidão das assinaturas será definida na implementação. |
| `data/remote/auth` | Implementa CookieJar, filtro de validade/origem, transporte Retrofit de autenticação, custódia e commit de cookies por geração. Adapta `CoreSessionInterceptor` para recuperação e retry sem construir Cookie manualmente; Bearer Core deriva do jar privado. Cliente de auth não usa interceptor recursivo; pode compartilhar custódia da geração, com política de envio adequada ao contrato. |
| `data/secrets` | Persiste registro versionado do refresh cookie e metadados com a criptografia existente; migra/remove chaves legadas conforme decisão. Reutiliza infraestrutura criptográfica com mudança mínima; não colocar Cookie de OkHttp no domínio para reutilizar SecretStorage. Interface adicional só se necessária para isolar persistência/testes. |
| `app/di` | Constrói jar/store e clientes com escopo consistente, liga portas a implementações e evita ciclo `SessionUseCase -> AuthRepository -> cliente -> SessionUseCase`. Cliente de autenticação não depende do interceptor de operações protegidas. |
| `presentation` | Continua usando casos de uso/SessionState, preserva mensagens e navegação. Não conhece headers, TTL ou valores de tokens. |

Reavaliar `AuthTokens`, `AuthResult.Success(tokens)`, `AuthRepository.refresh(refreshToken)`, usos de `SessionSnapshot.accessToken` e registros `Secrets.accessToken/refreshToken`. Remover referências obsoletas de autenticação, sem remover o armazenamento genérico se tiver outros usos. Adaptar mocks para resultados sem segredos; testar cookies no transporte real controlado, pois um mock de repository não exercita CookieJar.

Os callbacks CookieJar são síncronos; DataStore é suspenso. Definir leitura inicial e gravação em dispatcher de I/O, conclusão durável e propagação de erros sem I/O no main thread ou deadlock com mutex/refresh. Não lançar persistência fire-and-forget e depois reportar login bem-sucedido. Testar saturação/concorrência entre cliente de operação e cliente de refresh.

Ambientes: produção Android com Keystore/DataStore, testes JVM com relógio/store falsos e MockWebServer, instrumentados com armazenamento real. Somente dados sintéticos em fixtures. Nenhuma nova dependência é obrigatória nesta spec; confirmar versões resolvidas antes da implementação.

### Configuração de ambiente e envio mínimo

- AUTH_BASE_URL configura a base pública completa terminada em barra, incluindo prefixo do gateway se houver. Nenhum prefixo fixo /api/ é inferido. O padrão é uma origem .invalid, sem servidor de produção presumido.
- Bases de Auth/Core de produção exigem HTTPS. HTTP de fixtures JVM não altera a política do APK.
- Domain aprovado nesta entrega é o host exato da base Auth; hostOnly é preservado. Domain abrangente exige configuração futura explícita e não é aceito automaticamente. Path recebido é preservado, sem ampliação. HttpOnly é obrigatório.
- Login Auth: nenhum cookie/Bearer. Refresh Auth: somente refresh elegível. Core: somente Bearer do access privado para a origem exata. Chatbot: cliente independente sem essas credenciais. Redirects desabilitados.
- Persistência versionada inclui todos os refresh cookies ativos permitidos por identidade; access nunca é serializado. Dados legados são apagados, não convertidos.

### Sequência de execução proposta

1. Resolver contrato e decisões Open que condicionam comportamento; atualizar esta spec para Ready. Definir matriz de origem/endpoint/cookie e política de upgrade.
2. Red: adicionar testes de identidade, TTL, persistência seletiva, falhas e geração antes do jar/store. Green: implementar custódia em data. Refactor: eliminar duplicação mantendo evidência.
3. Red: adaptar testes de domínio para resultados sem valores, restore/refresh compartilhado e cache. Green: migrar portas/use cases/mocks; validar DI sem ciclos.
4. Red: exercitar Set-Cookie/Cookie no transporte, incluindo resposta antiga que chega durante novo login. Green: conectar clientes/interceptor e migração local; substituir a fonte do Bearer Core e remover store de tokens antigo.
5. Executar `testDebugUnitTest`, `spotlessCheck`, `detekt`, `lintDebug` e `assembleRelease`; executar `connectedDebugAndroidTest` com dispositivo disponível. Reportar indisponibilidade sem alegar aprovação. Cobertura combinada mantém 80% linhas/70% branches; relatório unitário isolado não comprova o gate combinado/CI.

## Decisions and open questions

Decisões do solicitante em 13/10/2026; contrato consultado em DS_Auth/README.md e docs/authentication-api-plan.md. Implementação autorizada com configuração de deployment pendente; escolhas técnicas aprovadas por autorização do solicitante para seguir o plano.

| Item | Status | Source or decision owner |
| --- | --- | --- |
| TTL recebido no cookie é fonte da verdade; access em memória, somente refresh persiste. | Decidido | Solicitante; substitui seis meses fixos. |
| Seguir DS_Auth: nomes access_token/refresh_token, login com platform=mobile, refresh sem JSON, par obrigatório em sucesso, rotação obrigatória e Bearer Core derivado do jar. | Decidido | Solicitante e contrato DS_Auth. |
| Sucesso sem par válido ou cookies de autenticação sem Max-Age/Expires bloqueia sessão e mostra erro inesperado. | Decidido | Solicitante. |
| Envio mínimo: sem cookies no login/Core/chatbot; refresh somente em refresh/logout Auth aprovado. | Decidido | Solicitante, minimização de exposição. |
| Origens públicas e Domain/Path/Secure/SameSite por ambiente. | Open — bloqueia deployment | Configurações ainda não definidas; explicar efeitos antes de escolher. |
| Apagar credencial legada sem TTL/escopo e exigir novo login. | Decidido | Solicitante. |
| Refresh expirado na abertura mostra aviso de sessão expirada. | Decidido | Solicitante. |
| Limpeza de cache na abertura com refresh expirado. | Decidido | Solicitante, aprovação em 13/10/2026. |
| 429 mostra mensagem amigável sobre limite ultrapassado e pede aguardar; sem retry automático. | Decidido | Solicitante. |
| Copy de 429 e bloqueio até Retry-After válido, sem nova chamada automática. | Decidido | Solicitante autorizou implementação seguindo o plano, 13/10/2026. |
| Processar Set-Cookie elegível também em erros, sem inferir garantia do servidor sobre preservação. | Decidido | Solicitante: cliente ingênuo quanto à garantia. |
| Captura isolada e commit condicionado à geração corrente. | Decidido | Solicitante; detalhes técnicos e prova AC-09 ainda necessários. |
| Falha de gravação após rotação remota encerra sessão, exige login e mostra erro inesperado. | Decidido | Solicitante. |
| Revogação local destrói chave exclusiva antes da remoção; remoção serve de fallback se a chave falhar. Falha simultânea permanece erro local e limitação durável. | Decisão técnica | Implementação autorizada; sem alterar AES-GCM ou chave de outros segredos. |
| Cancelamento após início da persistência: concluir etapa curta de persistência/commit antes de propagá-lo, condicionando publicação à geração corrente. Falha de gravação bloqueia sessão e exige login com erro inesperado. | Decidido | Solicitante, aprovação em 13/10/2026. Encerramento abrupto não garante conclusão. |

## Implementation outcome

Implementado em 13/10/2026 na branch existente. O cliente usa RetrofitAuthRepository, sem tokens em resultados ou snapshots de domínio. O DTO envia platform=mobile explicitamente. Cada chamada Auth tem um jar isolado para capturar Set-Cookie; o commit verifica geração sob mutex, valida o lote final, persiste somente refresh e então publica memória. A leitura para envio usa snapshot atômico de geração/cookies. Callbacks não fazem I/O nem dependem de ThreadLocal.

SessionUseCase coordena login/restauração, refresh compartilhado e revisão, incluindo valores sintéticos iguais. Limpeza tenta revogação e cache e avança a geração mesmo se a exclusão falhar. CoreSessionInterceptor envia apenas Bearer derivado do access privado e limita a repetição após 401. O fetch simulado de big numbers também valida/recupera acesso antes de uma operação protegida. Access expirado não provoca encerramento enquanto houver refresh renovável.

EncryptedAuthCookieStore armazena um lote versionado de registros refresh com AES-GCM/Keystore, reutilizando a implementação criptográfica existente com alias exclusivo. Destruição de chave precede remoção, que também é tentada quando a chave falha. Ciphertext sem a chave original não restaura sessão. Falha simultânea de ambos os mecanismos bloqueia a sessão atual e produz erro local; não garante revogação durável em um reinício. Não há promessa de atomicidade com Room. Tipos serializados legados foram movidos para data e permanecem apenas para upgrade/testes do armazenamento genérico, sem uso pela autenticação ativa.

429 mostra a mensagem aprovada e bloqueia a ação durante Retry-After válido, numérico ou data HTTP; o fim da espera não faz chamada automática. Login mapeia platform_not_allowed para a mensagem correspondente. Credenciais/cookies não são enviados a logs ou UI.

### Acceptance evidence mapping

| Critérios | Evidência |
| --- | --- |
| AC-01–05, AC-10 | AuthCookieCustodyTests: persistência seletiva, metadados, limite exato, precedência Max-Age, identidades/paths, exclusão em erro, lote incompleto, falha de escrita e cancelamento. AuthCookieStoreIntegrationTests foi compilado; execução Android pendente. |
| AC-06–09 | CoreSessionInterceptorTests e RetrofitAuthRepositoryTests: headers reais, isolamento de origem/porta, redirects, preflight de access expirado, refresh compartilhado e respostas HTTP antigas de sucesso/exclusão após novo login, seguidas de recriação da custódia. |
| AC-10–13 | SessionUseCaseTests, CredentialRevocationTests e SessionBigNumbersTests: erros locais, cleanup com falha, geração avançada, cache protegido, restauração ausente/expirada, rejeição e 429 transitório. Home/Login ViewModel tests continuam passando; navegação instrumentada foi adaptada e compilada. |
| AC-14 | AuthCookieStoreIntegrationTests contém upgrade com valor legado sintético; compilado, execução pendente. Inspeção manual confirma exclusão, sem conversão de validade. |
| AC-15 | AppModulesTest verifica o grafo e resolve repositório real, sessão e Core client sem ciclo. Revisão manual de imports não encontrou Android/OkHttp/Retrofit/Firebase no domínio nem tokens/cookies na apresentação. URLs e função de relógio explicitamente construídas são tipos externos no verificador Koin. |

Testes foram escritos durante a implementação antes de completar seus componentes. A primeira execução foi bloqueada por recursos ausentes preexistentes, portanto não estabelece Red comportamental. Uma execução posterior executou 86 testes e encontrou 11 falhas, incluindo a ausência de platform no payload HTTP, a verificação de parâmetros externos Koin e a renderização de falhas transitórias. As correções precederam a execução final aprovada. A recuperação mecânica de strings usou definições existentes no histórico do repo e não exigiu nova spec comportamental.

### Executed validation

Em Windows, com Java 17. Workaround somente de invocação: JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:/nonexistent-quistock-socket-dir para fallback TCP dos sockets locais Java; nenhuma configuração JVM persistente ou gate foi alterado.

Comando completo executado: gradlew.bat --max-workers=2 spotlessCheck detekt testDebugUnitTest lintDebug assembleRelease compileDebugAndroidTestKotlin --continue --console=plain.

| Check | Resultado |
| --- | --- |
| testDebugUnitTest | Passou: 93 testes, zero falhas/erros. |
| spotlessCheck | Passou. |
| Verificação adicional de AuthCookieCustodyTests | Passou: 11 testes, zero falhas/erros, após reforçar publicação somente depois da persistência e isolamento de rota/origem. |
| detekt | Passou: zero findings. |
| lintDebug | Passou; warnings existentes não foram silenciados. |
| assembleRelease | Passou; APK release gerado. |
| compileDebugAndroidTestKotlin | Passou; compilação não comprova execução. |
| connectedDebugAndroidTest | Não executado: nenhum dispositivo disponível. O AVD existente foi iniciado sem janela, permaneceu offline e foi encerrado. |
| Cobertura combinada | Não comprovada nesta entrega; execução instrumentada e gate combinado continuam pendentes. Limites de 80% linhas/70% branches preservados. |

Produção não foi exercitada: origens públicas, atributos de cookies e roteamento ainda dependem de configuração do ambiente. Fixtures HTTP são sintéticas e não aprovam um deployment.

Referências técnicas: [contrato CookieJar do OkHttp 4.12.0](https://github.com/square/okhttp/blob/parent-4.12.0/okhttp/src/main/kotlin/okhttp3/CookieJar.kt) e [Cookie do OkHttp 4.12.0](https://github.com/square/okhttp/blob/parent-4.12.0/okhttp/src/main/kotlin/okhttp3/Cookie.kt).

### Login observability (2026-10-15)

Requested addition: use the existing Logger/Logcat adapter. DEBUG records login start, successful session publication and cancellation; INFO records completion with a result category or an unexpected failure. Never log email, password, cookies, tokens, URLs, response bodies, exception messages or stack traces. Authentication outcomes and cancellation propagation remain unchanged.

Acceptance: a failed login emits DEBUG start and INFO with its result category, without input values (LoginUseCaseTests); existing login/session/DI checks validate integration. Manual Logcat check: filter tag QuiStock and operation=login; Android execution pending.

Validation for the logging addition: spotlessApply and detekt passed; instrumented Kotlin sources compiled in the first run. The targeted unit run executed 12 tests with one failure: the isolated DI fixture lacked Logger. The fixture was corrected. The repeat was blocked during main compilation by an unrelated local configuration change: RetrofitModule references R.string.backend_base_url, which is no longer generated. The corrected DI test has not been confirmed passing; no device tests were executed. No complete Red–Green claim is made for this addition.

### Auth transport diagnostics and XML configuration (2026-10-15)

The user tests on a physical device against a remote API and configures Auth in config.xml. Inspection of the previous merged debug resources confirmed the generated dummy Auth origin had replaced that XML value. Remove the Gradle Auth resValue and environment fallback: auth_base_url in XML is now the Auth source of truth. Core configuration is unchanged. No private endpoint is recorded here.

DEBUG transport events expose only operation, HTTPS/placeholder/base-path flags and HTTP status. INFO failures expose operation, bounded DNS/connection/timeout/TLS/protocol/I/O category and elapsed milliseconds. No URLs, messages, stack traces, headers, bodies, credentials or cookie values are logged. UI result mapping remains unchanged. Acceptance: AuthTransportDiagnosticsTests checks categories and safe context with a synthetic failure; inspect merged resource selection without printing its value. A physical-device retry is still required.

Separate findings: root-relative /login and /refresh discard any base path; the cookie jar still authorizes auth/refresh. API prefix/refresh alignment needs confirmation against the remodeled contract. SDK Auth/Core qualifiers also reference the opposite resources; this separate chatbot/SDK wiring was not changed as part of the login transport diagnosis.

Validation of transport diagnosis: spotlessCheck, detekt and the targeted AuthTransportDiagnosticsTests/LoginUseCaseTests/LoginViewModelTests/AppModulesTest run passed. The initial combined formatting/analysis run encountered an internal Detekt parser exception; rerunning after formatting completed passed. Resource inspection confirmed merged auth_base_url equals config.xml and no longer uses the dummy host (values withheld). Physical-device/API verification remains pending. Historical cookie transport suites were not rerun against the remodeled endpoints.

### Restore after API route remodeling (2026-10-15)

The current Auth transport uses root-relative /login and /refresh. Cookie sending must authorize exactly the same /refresh route, with unchanged origin/port, Secure, expiry and Cookie.matches constraints. Previously the jar still authorized auth/refresh, causing a valid restored refresh to be treated as absent and SessionUseCase to expire/revoke it before any HTTP request.

Acceptance: after successful login, recreate custody/repository/session using the same persisted refresh, restore, send only the refresh cookie to /refresh, become Active, and persist the rotated successor. RetrofitAuthRepositoryTests.reopeningRestoresRefreshCookieAndRenewsSessionAtCurrentRoute reproduces the failure. Update existing HTTP fixtures and cookie-scope checks to the remodeled routes; obsolete auth/refresh must receive no cookie. Cookie path metadata is never widened to make a request succeed. Actual Android persistence/device execution remains separate from the synthetic recreation test.

Validation: the new recreation test first failed with expected Active but actual Expired. After sharing the /refresh route between transport/preflight/jar and adapting historical fixtures, spotlessCheck, detekt and the complete testDebugUnitTest suite passed (96 tests, 0 failures/errors). Android device reopening was not executed here. A refresh deleted by the previous buggy expiry cannot be recovered; one fresh login is required after installing the fix.

### Startup navigation after restored refresh (2026-10-15)

The navigation graph starts at splashFragment. Once session restoration becomes Active, navigate from splash or login to Home, clearing the startup back stack as already implemented by MainActivity. A transient restore Failure must also leave splash for Home retry, preserving session/cache behavior. Active sessions on protected destinations must not be redirected. Restoring remains hidden; SignedOut/Expired/LocalFailure keep their existing destinations.

Acceptance: SessionNavigationTests verifies Active and transient Failure from splash reach Home, while Active on Home does not navigate. Existing Android recoveryOpensHomeAndExpirationRemovesProtectedBackStack checks navigation when executed on a device. The generic resource-close warning has no confirmed origin from the available evidence and is not treated as proof of an Auth HTTP leak.

Navigation validation: the three focused unit tests first produced two failures (Active and transient Failure from splash); preserving Home passed. After the fix, spotlessCheck, detekt and the complete unit suite passed (99 tests, 0 failures/errors). No device/instrumented execution or attribution of the close warning is claimed.

Post-rebase validation on the current main → homepage → login stack: 102 unit tests passed with zero failures/errors; spotlessCheck, detekt, lintDebug, assembleRelease and compileDebugAndroidTestKotlin passed. Instrumented tests were compiled but not executed on a device. The stack retains the main UI changes and the login-specific refresh/splash fixes.

### Review cleanup (2026-10-15)

Mechanical refactor; no HTTP or UI behavior changes. AuthApi and LoginRequest have dedicated files. The DI module owns the AuthApi factory, injected into the repository as a function. Each invocation builds a client with the request's isolated jar; repository/custody remain singletons. Existing RetrofitAuthRepositoryTests, CoreSessionInterceptorTests, AuthTransportDiagnosticsTests and AppModulesTest verify transport behavior, isolation and wiring (AC-06/09/15).

Approved simplification: remove the unused generic SecretStorage API, registry, legacy token models, DI binding and obsolete login result types. Keep SecretCipher and the dedicated AuthCookieStore. On Android upgrade, remove only refresh_token from the previous secrets DataStore, preserving unrelated entries; deletion failures propagate before restoration, as before. No decryption or TTL conversion is needed. AC-14 is covered by AuthCookieStoreIntegrationTests using synthetic encrypted legacy data, repeated startup and preservation of an unrelated entry. Existing AC-01/03/10 tests retain encryption, recreation and revocation coverage. JVM tests cannot exercise Android DataStore/Keystore; device execution is required.

Validation for this cleanup (2026-10-15): testDebugUnitTest passed (74 tests, no failures/errors/skips); spotlessCheck, detekt and lintDebug passed. The three AuthCookieStoreIntegrationTests passed on a connected Android 12 device, including encrypted recreation, key revocation and legacy cleanup with unrelated preference preservation. Instrumented test compilation also passed. assembleRelease passed. This targeted device run does not establish the combined CI coverage gate.

Initial Gradle attempts failed during Windows socket initialization because the environment's temporary path was too long. A short jdk.net.unixdomain.tmpdir supplied only to the validation processes resolved startup. An overloaded combined run was interrupted; checks were repeated with two workers. No project quality gate or hook was disabled. Refactoring preserves existing behavior, so no behavioral Red/Green cycle is asserted.
