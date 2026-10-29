# Big numbers da Home com dados da API Core

**Status:** Implemented — contracts approved on 2026-10-29; execution checks in progress.
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

## Approved implementation contract (2026-10-29)

Core exposes `GET dashboard/summary?store_id=<SQL id>`, relative to the configured
Core base URL. The existing Core client sends Bearer and preserves gateway prefixes.
The server authorizes the requested store; the client binds the response to the requested
store and session generation. No response scope field or new route is needed.

The three cards show near_expiry_products, stockout_products + overstock_products,
and active_actions. All four required JSON fields must be nonnegative integral Longs;
missing/null/fraction/overflow is a contract failure, never a zero fallback. The combined
count must not overflow. Existing Core rules remain: expiry <= configured default 30 days
includes expired positive-stock products; active actions exclude APPROVED.

Online entry (including return from another destination) and manual retry refresh the
profile and summary. Rotation/reobservation shares in-flight work. Existing Home alerts
remain independent. New code extends the current HomeViewModel and CoreHttpClient;
old qualifier warnings and references to a missing ViewModel no longer apply.

### Eligibility and offline access

Auth validates current platform and exactly one active store for mobile EMPLOYEE/MANAGER
at login and refresh. It persists platform in MongoDB. Store loss returns 403/no_active_store;
Core returns 403/NO_ACTIVE_STORE for missing active assignment, including GET profile.
These are terminal eligibility failures, unlike CSRF 403 and transient 429/503/network errors.
The prior decision to keep users without a store on Home is superseded: access is blocked.

After a successful online profile, persist the Core user/store binding with an opaque local
session ID in the encrypted refresh record. Preserve the local ID on refresh, replace on
login, and never decode JWT or use Firebase as identity. Old local records restore online
but cannot select offline cache until profile validation. Old server sessions without
platform require one new login after Auth deployment.

Cache only the four counts, opaque scope IDs, schema version and successful receipt time.
Use a dedicated DataStore key coordinated atomically with credential removal and identity
replacement. This avoids a relational database and prevents a queued stale write from
repopulating a logged-out or replaced scope. No domain session lock is held during cache I/O.

A distinct read-only session state permits cold reopening after a transient recovery failure
only when the protected binding, cache and unexpired refresh record agree. It permits only
Home indicators, manual recovery and logout; no profile/alert details/chatbot/protected actions.
Never mark this state as authenticated Active. Protocol errors, definitive denials, corrupt
credentials, missing binding/cache or expiry do not authorize local access. Revalidate Auth
and profile before releasing online functionality. No automatic request on reconnection.
Server-side revocation while disconnected is discovered on reconnection or local refresh expiry.

Logout, definitive expiry, account/store change and confirmed access loss immediately hide old
values and revoke cache eligibility. Late responses cannot publish or save into another session.
Cancellation propagates. Expired local eligibility is checked on render/resume and while visible.

### Presentation, failure and freshness

Replace static numbers with loading or an em dash until real data exists. Publish the three
cards together; valid zero is displayed as zero. Preserve authorized cache on transient failure.
Show manual retry, accessible labels and locale-aware full integer formatting. Respect 429
Retry-After (seconds or HTTP date); expiry only enables retry, never starts a request.
Core 401 uses the existing single refresh/retry; other statuses never trigger refresh.

Compare receipt/current dates in America/Sao_Paulo. Same date is current; a different date
(including clock rollback) shows “Dados possivelmente desatualizados. Última atualização: {data}.”
Date rollover updates the warning without a network call. Failed requests never advance the
saved date. Cache write failure keeps valid remote data in memory and shows “Indicadores
atualizados, mas não foi possível salvá-los para acesso offline.” Corrupt/version-mismatched
summary cache is unavailable and does not prevent remote loading. No sample-number fallback.

## Acceptance criteria and planned evidence

| ID | Observable result | Evidence |
|---|---|---|
| AC-01 | Correct store query, gateway prefix, Bearer transport, no Auth cookies | Controlled HTTP/DI tests |
| AC-02 | 4/2/3/5 maps to 4/5/5; integer and sum validation | Mapper tests |
| AC-03 | Zero counts are valid | Mapper and UI tests |
| AC-04 | Invalid fields/body/status fail atomically | Mapper/repository tests |
| AC-05 | Loading/error/manual retry; no fake counts or duplicate calls | Home tests |
| AC-06 | New entry reloads; recreation shares existing work | Home lifecycle tests |
| AC-07 | Transient failures preserve last valid cache/date | Use case/session tests |
| AC-08 | 403/429/contract failures do not renew; Retry-After enforced | HTTP/use case tests |
| AC-09 | First 401 renews once; definitive failure clears scope | Session/interceptor regression |
| AC-10 | Transient recovery allows only eligible local read | Session offline tests |
| AC-11 | Late result cannot publish in a new session | Deterministic concurrency tests |
| AC-12 | Cancellation/view destruction does not become a UI failure | Home tests |
| AC-13 | Locale, full integers, warning and retry accessible | UI tests and manual TalkBack |
| AC-14 | Koin resolves Core integration and existing alerts | DI tests |
| AC-15 | Core filters products and actions by authorized store | Core authorization/integration tests |
| AC-16 | Cold reopening reads only the same protected session/user/store | Persistence/recreation tests |
| AC-17 | Sao Paulo midnight changes warning without fetching | Clock-based tests |
| AC-18 | Only success atomically changes all counts/time | Cache/use case tests |
| AC-19 | Corruption/version/write failure never fabricates success | Storage tests |
| AC-20 | Logout/store replacement fences pending writes | Storage concurrency tests |
| AC-21 | 429 cooldown survives Home navigation within process | Use case tests |
| AC-22 | Missing active store blocks login/refresh/profile; no global/cache fallback | Auth/Core/Mobile tests |

All product decisions and required contracts are resolved by the approved implementation plan.
No ML, ERP mutation, new action workflow, redesigned screen or changes to website eligibility
are in scope. Instrumented tests require a device/emulator; missing execution is reported.

## Plano de verificação e resultado

Na implementação: escrever testes de comportamento primeiro (Red), implementar o
mínimo para passarem (Green) e refatorar preservando as fronteiras (Refactor).
Executar `.\gradlew.bat testDebugUnitTest spotlessCheck detekt lintDebug assembleRelease`.
Executar `connectedDebugAndroidTest` com dispositivo/emulador disponível; registrar
limitação se ausente. O gate CI continua 80% linhas/70% branches com cobertura
combinada unitária/instrumentada; relatório unitário local não comprova esse gate.
Smoke manual em desenvolvimento: login, resumo, comparação com resposta Core autorizada
e retry após indisponibilidade, sem registrar credenciais, payload pessoal ou URL privada.

Implementation in progress; do not treat planned checks as executed evidence.
Completar no PR de implementação: critérios entregues, evidência executada e diferenças aprovadas.
