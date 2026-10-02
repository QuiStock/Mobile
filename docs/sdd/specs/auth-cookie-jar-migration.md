# Migração da autenticação para cookies com persistência seletiva

**Status:** Draft
**Issue:** none — evolução do fluxo [QUIS-128](QUIS-128-login-client-side.md)
**Product decision owner:** Solicitante; contrato HTTP e decisões pendentes: Open

## Problem and goal

Substituir a custódia manual de tokens e o envio de Bearer por autenticação via cookies, respeitando sua validade e mantendo a recuperação de sessão ao reabrir o aplicativo. Requisito informado pelo solicitante em 02/10/2026: access token com TTL de 5 minutos; refresh token com TTL de 6 meses; somente o refresh deve persistir ao fechamento do app.

Estado verificado: `SessionUseCase` mantém tokens em campos e `SessionSnapshot`, grava ambos via `SecretStorage` e coordena restore/refresh por geração e revisão. `Secrets` configura access em memória e refresh em DataStore criptografado com Android Keystore/AES-GCM. `CoreSessionInterceptor` injeta `Authorization: Bearer`, compartilha refresh via caso de uso e limita a repetição após 401. `AuthRepository` recebe/devolve valores de tokens; sua implementação atual é `MockAuthRepository`. A API real não está integrada. O OkHttp dos testes está na versão 4.12.0; há MockWebServer disponível.

Esta spec substitui, para a futura migração, as regras de QUIS-128 sobre validade naive, custódia manual e transporte Bearer. Preserva seus comportamentos de UI, cache, falhas transitórias, isolamento e concorrência, salvo alterações explícitas abaixo. A spec anterior permanece como registro da etapa implementada.

## Scope

**In scope:** CookieJar específico da autenticação; validade obtida de `Set-Cookie`; access somente em memória; refresh persistido com metadados e criptografia; envio pelo header `Cookie`; recuperação e renovação; isolamento da Core/chatbot; adaptação dos contratos internos de domínio e DI; migração dos dados locais; testes determinísticos com transporte controlado.

**Out of scope:** inventar endpoints, nomes de cookies, payloads ou códigos da API; interpretar JWT; refresh por timer/background; alterar telas/copy/cadastro/chatbot; implementar logout remoto não contratado; trocar arquitetura, módulos, gates ou mecanismo criptográfico sem necessidade demonstrada. A integração de produção depende do contrato real; fixtures HTTP não o definem.

## Expected behavior

### Contrato e validade

- Login e refresh recebem tokens por `Set-Cookie`, e operações autenticadas enviam cookies elegíveis por `Cookie`. Remover a injeção de Bearer e o transporte manual de refresh no corpo/header quando o contrato real estiver confirmado.
- Os TTLs de 5 minutos e 6 meses são regras do servidor. O cliente usa `Max-Age`/`Expires` interpretados pelo OkHttp, sem criar uma nova validade ao carregar do disco nem deduzi-la do JWT. Se ambos estiverem presentes, `Max-Age` prevalece. A definição de 6 meses em segundos/calendário é Open; não assumir 180 dias.
- Cookie com `expiresAt <= agora` não pode ser enviado. `Cookie.matches(url)` verifica domínio/caminho/Secure, mas não substitui a verificação de expiração. Usar relógio controlável nos testes e tempo de parede para timestamps HTTP; não usar sleeps reais de cinco minutos/seis meses.
- Atualizar cookies por identidade `(nome, domínio, caminho)`. `Set-Cookie` expirado ou `Max-Age=0` elimina o correspondente também do disco. Lotes podem conter vários headers, rotação e remoção; ausência de um cookie em uma resposta não significa automaticamente apagá-lo. Semântica de rotação/par obrigatório e cookies sem prazo fica Open.
- Aceitar/persistir apenas cookies de autenticação explicitamente identificados pelo contrato, em origens e escopos aprovados. Não persistir cookies desconhecidos por terem `persistent=true`. Não ampliar Domain/Path nem fabricar metadados.

### Custódia, persistência e recuperação

- O jar é a única fonte de verdade dos cookies ativos. Access nunca é serializado em disco, backups, estado salvo de UI ou cache de domínio. Refresh fica em memória e em armazenamento criptografado, com valor e metadados necessários para reproduzir identidade, expiração e restrições de envio (inclusive hostOnly/Secure/HttpOnly).
- Ao perder o processo, perder access. Ao iniciar, carregar somente refresh válido e permitido; obter novo access por refresh antes de uma operação protegida. Reabrir antes do prazo não estende a validade do refresh.
- Ausência normal de refresh na abertura mantém o login sem aviso e sem chamada de refresh. Refresh expirado na abertura não é enviado; o aviso e a limpeza do cache nesse caso são uma decisão Open. Durante uma sessão existente, ausência/expiração definitiva de refresh impede renovação e encerra a sessão conforme QUIS-128.
- Expirar access não encerra a sessão se houver refresh válido. Na próxima operação protegida, recuperar access antes de enviá-la; não agendar renovação em background. Na restauração, falhas transitórias preservam refresh válido/cache e seguem a UI de QUIS-128.
- Falhas de leitura/descriptografia/gravação mantêm os resultados locais de QUIS-128: erro inesperado, sem sucesso de login/restauração nem uso remoto de par parcialmente instalado. A publicação em memória segue a persistência bem-sucedida. A integração precisa propagar falhas do jar, sem escondê-las como ausência de cookies ou sucesso HTTP.
- Encerramento definitivo remove cookies de memória/disco e cache do usuário. Se a remoção do disco falhar, bloquear a sessão atual; antes da entrega, definir e testar como impedir restauração futura de credencial revogada localmente (Open). Não prometer atomicidade entre DataStore e Room.

### Renovação, concorrência e isolamento

- Preservar um refresh em andamento por geração, reutilização da revisão já renovada após 401 atrasado, uma repetição da operação após renovação e encerramento por segundo 401. Refresh/login não passam pela recuperação recursiva de operações protegidas.
- Não tratar rede, timeout ou 5xx como rejeição definitiva. Preservar cache e refresh ainda válido, mantendo mensagens e retry por ação da pessoa de QUIS-128. O TTL continua correndo durante indisponibilidade.
- Publicação dos cookies recebidos precisa estar vinculada à geração da requisição. Resposta antiga, inclusive `Set-Cookie` de sucesso, exclusão ou rotação, não pode alterar memória/disco de uma sessão nova. O controle deve ocorrer antes da mutação no jar, e não apenas após `auth.refresh` retornar. CookieJar recebe URL/cookies, não geração: um jar global sem contexto é insuficiente.
- Proposta de implementação: contexto de transporte/jar por geração, com recebimento isolado dos cookies de login/refresh e commit condicionado à geração corrente. O mecanismo concreto será definido e testado antes da implementação; não depender de ThreadLocal para atravessar coroutines/threads. Login concorrente e operação antiga não podem publicar cookies nem repetir sob a identidade de outra conta.
- Escopo de cookie não substitui isolamento de cliente/origem. Manter restrição explícita de scheme/host/port, redirects desabilitados e cliente legado de chatbot independente. Cookie Domain amplo não autoriza enviar segredos a subdomínios ou portas fora da configuração aprovada. O escopo do refresh nos endpoints comuns depende de Path/origens do contrato (Open).
- Revisão deve avançar após renovação confirmada mesmo quando os valores sintéticos forem iguais. Sem expor valores de tokens para detectar mudanças.
- Não incluir cookies, `Set-Cookie`, credenciais ou valores de tokens em logs, exceções, analytics ou relatórios de testes.

## Acceptance criteria and evidence

| ID | Observable given / when / then | Planned test or check |
| --- | --- | --- |
| AC-01 | Dado login com cookies válidos, quando concluído, então refresh é persistido criptografado antes da publicação da sessão e access permanece apenas em memória. | Unitário jar/repository com ordem de commit; instrumentado DataStore/Keystore verificando arquivo e nova instância. |
| AC-02 | Dado cookie recebido, quando o relógio alcança exatamente `expiresAt`, então ele não é enviado; antes do limite permanece elegível. | Unitários com relógio falso, Max-Age/Expires e precedência; MockWebServer com header recebido. |
| AC-03 | Dado refresh persistido, quando o processo é recriado, então access não existe e refresh mantém expiração/escopo originais; restauração obtém novo access sem credenciais. | Unitário round-trip de todos os metadados; instrumentado recriação do armazenamento e sessão. |
| AC-04 | Dado access expirado e refresh válido, quando inicia operação protegida, então há um refresh compartilhado e a operação usa novo cookie, sem timer. | Transporte controlado/relógio falso e teste concorrente; ausência de chamadas durante avanço ocioso. |
| AC-05 | Dada resposta com rotação ou exclusão, quando aplicada, então substitui apenas a identidade correspondente e apaga do disco refresh removido. | Unitários com identidades distintas, vários Set-Cookie e Max-Age=0; round-trip persistente. |
| AC-06 | Dadas chamadas Core, chatbot, outra origem/porta ou redirect, quando executadas, então somente destinos aprovados recebem cookies elegíveis e nenhum recebe Bearer legado. | MockWebServer, duas origens/portas, Domain/Path/hostOnly/Secure e redirect; teste DI. |
| AC-07 | Dado primeiro 401, quando refresh sucede, então repete a operação uma vez; segundo 401 encerra sem recursão. | Adaptar `CoreSessionInterceptorTests` para cookies e contagem de chamadas. |
| AC-08 | Dadas operações concorrentes/401 atrasado, quando renovadas, então compartilham um refresh/revisão, inclusive com valores iguais. | Adaptar testes determinísticos de sessão/HTTP com barreiras. |
| AC-09 | Dado novo login durante requisição antiga, quando chegam Set-Cookie antigos de sucesso/remoção ou rejeição, então memória, disco, sessão e cache novos permanecem intactos e não há retry sob nova identidade. | Teste HTTP concorrente que observa callbacks reais do jar, seguido de nova instância do armazenamento; testes de cache por geração. |
| AC-10 | Dado erro de leitura/gravação/criptografia ou cancelamento durante instalação, quando tratado, então não publica sessão parcial, não autoriza operação remota e apresenta erro local previsto. | Unitários com falha injetada por etapa, cancelamento e limpeza falhando; instrumentado da mensagem/recriação após resolução da política Open. |
| AC-11 | Dado refresh indisponível por rede/timeout/5xx, quando falha, então preserva refresh ainda válido/cache, aviso e retry explícito; não há retry ao voltar conexão. | Adaptar `SessionUseCaseTests`, `HomeViewModelTests`, `HomeFragmentTests` e HTTP. |
| AC-12 | Dado refresh ausente/expirado, quando restaura ou tenta renovar, então não o envia nem revive sua validade; ausência na abertura não mostra expiração e sessão existente sem renovação encerra. | Unitário relógio falso e navegação; completar caso de abertura com expirado após decisão Open. |
| AC-13 | Dada rejeição definitiva/segundo 401, quando encerra, então limpa cookies e cache, impede retorno às telas protegidas e mantém aviso existente. | Adaptar `SessionNavigationTests`, `SessionBigNumbersTests`, integração Room e persistência; falhas de remoção conforme decisão Open. |
| AC-14 | Dado armazenamento legado sem TTL/escopo, quando versão nova inicia, então aplica a política aprovada sem inventar validade e sem manter dois stores ativos. | Teste de upgrade com fixture criptografada sintética; critério dependente da decisão de migração. |
| AC-15 | Dada arquitetura migrada, quando revisada, então domain/presentation não importam OkHttp/Android/Retrofit nem expõem tokens/cookies; DI resolve clientes e sessão sem ciclos. | Teste de DI e revisão de imports/contratos; checagem manual justificada pela ausência de gate arquitetural específico. |

## Technical impact

### Responsabilidades propostas

| Camada | Responsabilidade e mudanças |
| --- | --- |
| `domain` | `SessionUseCase` mantém estado, geração/revisão, coordenação de refresh e autorização de publicação/limpeza do cache. Remove custódia dos valores e dependência de SecretStorage para autenticação. `AuthRepository` passa a expressar login/refresh/restore/limpeza com resultados sem tokens e contexto opaco de sessão quando necessário. `SessionSnapshot` contém identidade da geração/revisão e disponibilidade sem valores. Exatidão das assinaturas será definida na implementação. |
| `data/remote/auth` | Implementa CookieJar, filtro de validade/origem, transporte Retrofit de autenticação, custódia e commit de cookies por geração. Adapta `CoreSessionInterceptor` para recuperação e retry sem construir Cookie/Bearer manualmente. Cliente de auth não usa interceptor recursivo; pode compartilhar custódia da geração, com política de envio adequada ao contrato. |
| `data/secrets` | Persiste registro versionado do refresh cookie e metadados com a criptografia existente; migra/remove chaves legadas conforme decisão. Reutiliza infraestrutura criptográfica com mudança mínima; não colocar Cookie de OkHttp no domínio para reutilizar SecretStorage. Interface adicional só se necessária para isolar persistência/testes. |
| `app/di` | Constrói jar/store e clientes com escopo consistente, liga portas a implementações e evita ciclo `SessionUseCase -> AuthRepository -> cliente -> SessionUseCase`. Cliente de autenticação não depende do interceptor de operações protegidas. |
| `presentation` | Continua usando casos de uso/SessionState, preserva mensagens e navegação. Não conhece headers, TTL ou valores de tokens. |

Reavaliar `AuthTokens`, `AuthResult.Success(tokens)`, `AuthRepository.refresh(refreshToken)`, usos de `SessionSnapshot.accessToken` e registros `Secrets.accessToken/refreshToken`. Remover referências obsoletas de autenticação, sem remover o armazenamento genérico se tiver outros usos. Adaptar mocks para resultados sem segredos; testar cookies no transporte real controlado, pois um mock de repository não exercita CookieJar.

Os callbacks CookieJar são síncronos; DataStore é suspenso. Definir leitura inicial e gravação em dispatcher de I/O, conclusão durável e propagação de erros sem I/O no main thread ou deadlock com mutex/refresh. Não lançar persistência fire-and-forget e depois reportar login bem-sucedido. Testar saturação/concorrência entre cliente de operação e cliente de refresh.

Ambientes: produção Android com Keystore/DataStore, testes JVM com relógio/store falsos e MockWebServer, instrumentados com armazenamento real. Somente dados sintéticos em fixtures. Nenhuma nova dependência é obrigatória nesta spec; confirmar versões resolvidas antes da implementação.

### Sequência de execução proposta

1. Resolver contrato e decisões Open que condicionam comportamento; atualizar esta spec para Ready. Definir matriz de origem/endpoint/cookie e política de upgrade.
2. Red: adicionar testes de identidade, TTL, persistência seletiva, falhas e geração antes do jar/store. Green: implementar custódia em data. Refactor: eliminar duplicação mantendo evidência.
3. Red: adaptar testes de domínio para resultados sem valores, restore/refresh compartilhado e cache. Green: migrar portas/use cases/mocks; validar DI sem ciclos.
4. Red: exercitar Set-Cookie/Cookie no transporte, incluindo resposta antiga que chega durante novo login. Green: conectar clientes/interceptor e migração local; remover Bearer/store de tokens antigo.
5. Executar `testDebugUnitTest`, `spotlessCheck`, `detekt`, `lintDebug` e `assembleRelease`; executar `connectedDebugAndroidTest` com dispositivo disponível. Reportar indisponibilidade sem alegar aprovação. Cobertura combinada mantém 80% linhas/70% branches; relatório unitário isolado não comprova o gate combinado/CI.

## Decisions and open questions

| Item | Status | Source or decision owner |
| --- | --- | --- |
| Ambos os tokens via cookie; access 5 minutos em memória, refresh 6 meses persistido. | Decidido | Solicitante, 02/10/2026. |
| Validade vem de Set-Cookie e não reinicia na leitura; sem parsing JWT/timers. | Proposto | Esta migração e preservação do escopo QUIS-128. |
| Recuperar antes da próxima operação quando access expirou. | Proposto | Evitar envio de cookie expirado sem encerrar sessão renovável. |
| Nomes exatos, origens auth/Core, Domain/Path, Secure/HttpOnly, TTL expresso pelo servidor e significado de seis meses. | Open — bloqueia integração real | Responsável pela API. |
| Rotas/métodos/payloads, sucesso sem cookie válido, cookies sem Max-Age/Expires, rotação obrigatória/opcional e classificação de rejeição definitiva. | Open — bloqueia comportamento dependente | Responsável pela API; não presumir todo 401 da autenticação como contrato definitivo. |
| Refresh pode ser enviado a todas as rotas Core ou apenas ao endpoint de renovação? | Open | Contrato de Path/origens; confirmar escopo mínimo necessário. |
| Registro legado só tem valor, sem expiry/escopo: invalidar e pedir login, ou migrar via endpoint compatível que devolva cookies válidos? | Open — bloqueia upgrade | Produto/API. Não atribuir seis meses novos ao token antigo. |
| Na abertura com refresh expirado: aviso de expiração e limpeza de cache ou entrada sem aviso? | Open | Produto; distinguir ausência normal e sessão encerrada durante uso. |
| Persistência/remoção falhando: garantia durável para impedir restauração de credencial descartada, inclusive após fechamento abrupto. | Open — bloqueia conclusão da persistência | Responsável técnico; manter bloqueio imediato e erro local previsto. |
| Mecanismo concreto de captura/commit por geração e atomicidade do lote, sem contexto disponível no CookieJar padrão. | Open técnico — resolver antes de ligar transporte | Responsável técnico; AC-09 exige teste do callback real. |

## Implementation outcome

Somente planejamento nesta mudança: nenhum código de produção alterado e nenhum teste comportamental executado. Critérios acima são evidências planejadas, não resultados. Preencher durante implementação com testes executados, decisões resolvidas e diferenças aprovadas.

Referências técnicas: [contrato CookieJar do OkHttp 4.12.0](https://github.com/square/okhttp/blob/parent-4.12.0/okhttp/src/main/kotlin/okhttp3/CookieJar.kt) e [Cookie do OkHttp 4.12.0](https://github.com/square/okhttp/blob/parent-4.12.0/okhttp/src/main/kotlin/okhttp3/Cookie.kt).
