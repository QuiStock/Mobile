# Project context for AI-assisted development

Updated on 2026-10-20 from repository files. This document guides discovery; confirm details in the code before changing behavior.

## Known state

- Criação de pedido de compra/promoção escrita em 29/10/2026 conforme
  `specs/create-interference.md`, ainda em andamento: funcionário ativo,
  riscos/análises de `/flows/current`, parâmetros humanos explícitos, pré-checagem
  de pendências, geração e encaminhamento separados. Rascunho em memória na
  Activity; transporte dedicado sem replay de escrita, com 30 segundos por chamada.
  Hub consulta ações reais; somente SENT_TO_MANAGER confirma envio. Home passa a
  usar riscos Core em vez de HIGH/LOW ou histórico para criação. Processamento de
  análise no login não foi integrado; sem análise atual elegível, seleção vazia.
  Nenhum Gradle/teste/build/lint/cobertura ou escrita real ao Core executado por
  instrução expressa do solicitante (wrapper limitado no sandbox). Testes escritos,
  revisão estática e PR draft não concluem a funcionalidade. AC-17 exige teste real
  na versão final. Ver comandos/rastreabilidade na spec. Gates preservados.


- `QuiStock` is an Android project with one Gradle module, `:app`, Kotlin, Java 17, Views/XML, ViewBinding, and the `com.quistock.quistock` package.
- The README describes Clean Architecture with MVVM and the dependency direction `presentation -> domain <- data`. The current structure uses `domain/port` and Koin dependency injection; follow the existing code where the guide is illustrative.
- The code includes login, personal registration, home, and chatbot areas. Login uses the DS_Auth contract through `RetrofitAuthRepository` and `SessionUseCase`, with isolated response-cookie capture, generation-checked commits and encrypted refresh persistence. For the CSRF correction, root routes on the same HTTPS Auth origin and compatible cookie attributes were confirmed by the requester; real deployment smoke verification remains pending. The chatbot uses a use case and a Retrofit repository for an internal API. These files do not establish that the product flows are complete.
- Public Auth/Core URLs come from `auth_base_url` and `core_base_url` in
  `app/src/main/res/values/config.xml`. Cloudinary public configuration uses that file too;
  Gradle does not load `.env` automatically. Do not record private endpoints in specs.
- There are unit tests for domain, ViewModel, dependency injection, and infrastructure, plus an instrumented login test. Their presence does not imply complete coverage of every flow.

- The cancelled indicator branch is no longer part of the login stack. Home retains main's static layout; SessionUseCase has no indicator/cache dependency. Login retains the Logger/Logcat adapter used for safe diagnostics.
- Profile logout now confirms the action, fences the session, clears local credentials and legacy user identity independently of a single bounded DS_Auth logout attempt, then clears navigation to Login. Local cleanup failures block protected content and offer local-only retry. See `specs/profile-logout.md`; execution checks for this delivery were deferred at the requester's instruction.

- Auth refresh now obtains an isolated, memory-only CSRF pair from `/csrf` and sends the JSON token in `X-CSRF-TOKEN` with refresh/CSRF cookies to `/refresh`. Protocol failures preserve eligible refresh and present manual recovery while protected navigation is hidden. HTTP follow-ups are blocked. See `specs/auth-refresh-csrf.md`; tests were written but execution/validation is deferred by the requester. Logout now has its own isolated CSRF exchange with one shared ten-second deadline; see `specs/profile-logout-csrf.md`. Execution checks remain deferred.

- Perfil agora tem implementação de consulta Core em `GET profile`, estado somente em memória por sessão e barreira de entrada coordenada por `SessionViewModel`/`MainActivity`. O modelo usa `UserRole`/`UserStatus`; a UI usa `displayName`. Home só é liberada para Funcionário (`EMPLOYEE`) ou Gerente (`MANAGER`) com loja vinculada. O mapper aceita somente EMPLOYEE, MANAGER e REGIONAL_MANAGER, conforme o enum confirmado pelo solicitante. REGIONAL_MANAGER é bloqueado por cargo; ADMIN e códigos legados são respostas inválidas, sem publicação do perfil. Headers, Editar perfil e Contato consomem o mesmo resultado. Sem recarga ao voltar do background; foto independente não bloqueia entrada. Ver `specs/profile-user-store-data.md`. Testes foram escritos, mas execução e validações foram delegadas ao solicitante; compatibilidade do vínculo de loja de gerentes no backend real não foi verificada.

- Rebase de perfil sobre CSRF/logout concluído: perfil e foto são descartados no início do logout, mantendo a barreira do perfil e a recuperação/diálogos de logout. Core usa apenas Bearer derivado do access cookie privado, com CookieJar vazio e remoção de headers Cookie/CSRF. O binding do chatbot legado foi renomeado para deixar explícito que não é o cliente Core autenticado. Testes de regressão foram escritos/adaptados, sem nova execução ou validação após o rebase por orientação do solicitante.

- Core e carregamento de perfil agora emitem eventos estruturados pelo Logger existente (tag QuiStock), com operation=core_profile/core_request, status/tentativa/duração, renovação, bloqueio e categorias de rejeição/decodificação, sem tokens, cookies, URLs, payloads ou dados pessoais. Ver `specs/core-request-diagnostics.md`. Testes escritos e verificações de execução continuam delegados ao solicitante.

- O ambiente consultado pelo solicitante retornou role=EMPLOYEE em /profile, divergindo dos códigos portugueses do DS_Backend local. Após a confirmação do enum atual, o domínio usa os cargos em inglês e o rótulo Funcionário; o mapper não aceita aliases legados. Ver `ProfileRoleMappingTests`, escritos mas não executados conforme orientação do solicitante.

## Sources of truth

- Rodada de criação recebida em 29/10/2026: solicitante informa aprovação das
  validações exceto Detekt. Relatórios locais lidos (sem execução pelo agente):
  290 unitários e 68 instrumentados sem falhas; JaCoCo combinado com 87,5% de
  linhas e 71,7% de branches; Lint sem erros/fatais, com 612 avisos. Detekt registra
  três ReturnCount e duas funções render com complexidade excessiva. Refatorações
  escritas preservam os comportamentos e a formatação local do solicitante,
  sem alterar gates/supressões. Sem nova execução de Gradle/testes/build/lint;
  aprovação do novo commit depende de outra rodada. AC-17 continua sem evidência
  detalhada registrada. Ver `specs/create-interference.md`.


- Relatório instrumentado de 29/10/2026 registra 69 aprovações e uma falha em
  70 testes. A falha restante de logout é uma espera pela raiz da Activity sem
  foco enquanto o aviso remoto está aberto. Teste ajustado para verificar e
  fechar a raiz específica do aviso antes de aguardar Login. Sem nova execução
  ou validação pelo agente; ver `specs/role-based-bottom-navigation.md`.

- Rodada de navegação recebida em 28/10/2026: relatório unitário das 17:32 tem
  268 testes sem falhas; instrumentação das 17:38 tem 70 testes com 14 falhas.
  JaCoCo disponível é das 11:50, anterior à rodada e sem o pacote novo de
  navegação; não estabelece a cobertura atual. Correções escritas para espera
  pela remoção do skeleton, pedido de IME ao editor no Samsung Android 12,
  isolamento de perfil/alertas nos testes de logout e teste de funcionário
  coerente com a barreira gerencial. Sem nova execução ou validação pelo agente;
  gates e exclusões preservados. Ver `specs/role-based-bottom-navigation.md`.

- Navegação inferior por cargo implementada em 28/10/2026 conforme
  `specs/role-based-bottom-navigation.md`: barra compartilhada na Activity normal,
  quatro abas para EMPLOYEE e Funcionários adicional para MANAGER; destinos,
  histórico de retorno à aba anterior, confirmação de saída na Home, ocultação
  com teclado e estado de busca/rolagem em memória por geração da sessão.
  Chatbot usa ViewModel da Activity com conversa em memória, preservada entre
  abas/recriação e descartada na expiração/logout/troca de geração. Demo mantém
  sua navegação e seus layouts. Testes unitários/instrumentados escritos e mocks
  do Chatbot adaptados; nenhuma execução, build ou validação pelo agente, por
  instrução do solicitante. Compilação, gates e checks de acessibilidade/visual
  aguardam os relatórios e a rodada manual do solicitante.

- Edição do próprio perfil implementada em 28/10/2026 conforme `specs/profile-edit.md`:
  PATCH parcial e conferência de resultado incerto, rascunho em ViewModel, galeria/câmera,
  preparo JPEG com orientação e upload unsigned isolado Cloudinary. Configuração pública usa
  `profile_cloudinary_cloud` e `profile_cloudinary_preset` em `res/values/config.xml`;
  sem configuração, nome/remoção
  continuam disponíveis. Testes unitários/instrumentados escritos; nenhuma execução,
  build ou validação por orientação expressa do solicitante. Provisionamento do preset,
  integração real e acessibilidade visual/assistiva aguardam sua rodada de validação.

- Relatórios do solicitante de 28/10/2026 motivaram correções dos 18 `MagicNumber`
  do Detekt atual e de três falhas instrumentadas: flags de foco do e-mail são
  reaplicadas após remover o keyListener; o setup do chatbot aguarda Home e o destino
  Chatbot com o IdlingResource de navegação compartilhado. Sem nova execução de
  testes/validações pelo agente; confirmação aguarda a próxima rodada do solicitante.

- Rodada combinada JaCoCo recebida em 28/10/2026: 81,0% de linhas e 65,3% de
  branches, abaixo do mínimo de branches. Testes adicionais de upload/download,
  cache, erros/concorrência do editor e renderização/opções de foto foram escritos.
  Transportes aceitam dependências internas substituíveis, preservando os clientes
  públicos isolados. Sem execução pelo agente e sem alterar gates/exclusões;
  novo percentual depende de outra execução do solicitante. Ver `profile-edit.md`.

- Home integra alertas Core via `/branches`, `/products` e `/flows`, com seleção no domínio e estados no ViewModel. Correção de 25/10/2026 usa estoque BigDecimal com até três casas efetivas e exibição sem unidade; cada produto consolida lotes por produto/loja. Histórico é selecionado pelo maior instante analysis_date, sem afirmar vigência; datas inválidas/ausentes ou empate ambíguo são incompatíveis. Validade usa produto e continua excluindo negativos, com possível ocultação de saldo válido aceita pelo solicitante. LOW mostra “Risco de excesso ou vencimento”; MEDIUM próximo da validade não tem promoção. Avisos de conferência ERP na Home e detalhes são exclusivos para MANAGER Ready da sessão ativa/geração atual, pelo binding compartilhado; nenhum timestamp foi inventado para detalhes sem solicitação real. Envios/aprovações remotas e auditoria seguem fora do escopo, sem simular confirmação registrada. Core autoriza por loja segundo seu responsável; fuso de negócio e permissão de aprovação de funcionário continuam pendências backend. Testes unitários/instrumentados escritos, sem execução, build, Gradle ou outras validações por instrução do solicitante. Ver `specs/home-priority-alerts.md`.

| Topic | Source |
| --- | --- |
| Intended architecture and commands | `README.md` |
| Quality rules and criteria for future work | `TODO.md` |
| Implemented behavior | `app/src/main`, `app/src/test`, `app/src/androidTest` |
| Build and coverage configuration | `app/build.gradle.kts` |
| Executed CI gates | `.github/workflows/ci.yaml` |
| Scope of a change | Current request, Jira issue, and relevant spec |

## Limits and precautions

- Revisão normativa de alertas em 25/10/2026: correção mobile pronta para decimais sem unidade, LOW “Risco de excesso ou vencimento”, MEDIUM sem promoção e uso consultivo de histórico com avisos/conferência somente para MANAGER da sessão atual. DTO/repositório Core locais expõem analysis_date a partir de pa.created_at; seleção pode usar maior instante com rejeição defensiva de data ausente/inválida ou empate ambíguo, sem afirmar vigência. Fuso Core e submissão/aprovação/auditoria real continuam fora da correção; aprovação atual tem apenas layouts. Não simular registro de decisão nem considerar avisos uma correção de autorização do backend. Ver seção normativa e critérios RC em `specs/home-priority-alerts.md`; histórico conflitante é não normativo. Nenhum código alterado ou validação executada nesta revisão.

- Refinamento de alertas em 25/10/2026: solicitante aprovou aceitar estoque fracionário com até três casas decimais, sem arredondar para inteiro (unidade do produto ainda sem fonte contratual), manter exclusão de produto com validade consolidada negativa aceitando possível ocultação de saldo válido, e renomear LOW para “Risco de excesso ou vencimento” mantendo abertura de promoção. Essas são regras aprovadas para adaptação futura; o código entregue ainda rejeita frações e usa o rótulo anterior. Sem execução de testes/validações nesta rodada documental.

- `TODO.md` contains proposals with entry criteria; do not implement items merely because they are listed.
- Confirm backend contracts, copy, navigation, permissions, and business rules against the request or an explicit source. Do not infer them from class names or layouts.
- CI runs debug/release builds, Spotless, Lint, Detekt, unit tests, and instrumented tests; the combined JaCoCo gate requires 80% line coverage and 70% branch coverage.
- Changes involving Firebase, the backend, or persistence must state the environment, affected data, and expected failures in the relevant spec.

## Home summary and local read-only access (2026-10-29)

PR #44 supersedes earlier global-summary and no-offline-cache assumptions. Core's
`dashboard/summary?store_id=` supplies the four card metrics; HomeViewModel preserves
priority alerts while loading summary separately. The encrypted refresh record now owns
an opaque local session ID and a validated Core user/store binding. A separate summary
key uses the same DataStore transaction boundary for atomic invalidation on revocation
or identity replacement; no Room/SQLite dependency is added. No tokens enter summary cache.

A distinct OfflineReadOnly session state permits only saved Home indicators, manual
recovery and logout until refresh expiry; it never authorizes Core calls. Missing/expired
binding, protocol errors and definitive eligibility failures cannot unlock local Home.
Server revocation while disconnected is detected at recovery or local expiry. Auth now
requires an active store for mobile EMPLOYEE/MANAGER at login/refresh; no-store users
are blocked, superseding the old PR #44 blank-cards/retained-session decision. Documentation
of planned checks is not test evidence; final executed results belong in the PR.
