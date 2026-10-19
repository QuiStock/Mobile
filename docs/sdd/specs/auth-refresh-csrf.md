# Correção de CSRF no refresh da Auth

**Status:** Ready for implementation — decisões resolvidas em 2026-10-19
**Issue:** none
**Product decision owner:** Solicitante
**Data da análise:** 2026-10-19

## Problem and goal

O Mobile envia `refresh_token` para DS_Auth sem o par CSRF exigido pela API. A API rejeita a chamada com 403 antes de rotacionar a sessão. O cliente classifica esse status como `UnexpectedError`; `SessionUseCase.localFailure()` remove as credenciais, embora a rejeição CSRF não estabeleça que o refresh seja inválido.

O objetivo é permitir a renovação e restauração da sessão pelo contrato atual de DS_Auth, preservando isolamento, validade e armazenamento protegido das credenciais. Requisito: solicitação do usuário nesta conversa. Esta entrega prepara somente a spec; não autoriza implementação enquanto existir qualquer ponto Open.

Base inspecionada: branch `fix/login-csrf-token`, commit `d8d74af`, igual à referência local `origin/main` na análise. Essa base não implementa logout remoto em `AuthApi`, `AuthRepository` ou `SessionUseCase`.

### Fontes do contrato

Documentação local de DS_Auth consultada em 2026-10-19:

- [README DS_Auth](../../../../DS_Auth/README.md), seções “Rotas”, “CSRF for refresh/logout” e orientação de Swagger.
- [Plano de autenticação](../../../../DS_Auth/docs/authentication-api-plan.md), atualização de segurança inicial, contrato de rotas/refresh e seção 8, integração Mobile. Exemplos históricos não substituem o contrato atual.
- Corroboração de implementação: [SecurityConfiguration](../../../../DS_Auth/src/main/java/com/quistock/auth/config/SecurityConfiguration.java), [CsrfController](../../../../DS_Auth/src/main/java/com/quistock/auth/controller/CsrfController.java) e teste `refreshAndLogoutRequireCsrfWhenARefreshCookieIsPresent` em [DsAuthApplicationTests](../../../../DS_Auth/src/test/java/com/quistock/auth/DsAuthApplicationTests.java).
- [Spec de custódia de cookies](auth-cookie-jar-migration.md) e [QUIS-128](QUIS-128-login-client-side.md) para garantias existentes do Mobile.

A análise comprova a divergência estática; não comprova configuração ou respostas do serviço implantado. Nenhum endpoint real foi chamado.

## Scope

**In scope:** obtenção do par CSRF da Auth antes de refresh; envio correto dos cookies/header; restauração após recriação; falhas de preparação/protocolo; proteção por geração e refresh compartilhado; critérios de teste e atualização da documentação afetada.

**Out of scope:** implementar logout remoto ou telas de perfil; alterar DS_Auth/Core/gateway; CSRF da Core; mudar autenticação Bearer da Core; cadastro/chatbot; persistir CSRF; agendar refresh; adicionar módulos/camadas; alterar gates ou criptografia. Logout futuro deve obter/enviar o mesmo tipo de par Auth, mas exige spec própria de fluxo, limpeza, concorrência e limites de rede.

## Expected behavior

### Contrato confirmado da API

| Operação | Entrada/envio | Resposta e semântica |
| --- | --- | --- |
| `GET /csrf` | Público, sem credenciais de login. | 200 com JSON `headerName`/`token`, cookie `AUTH-XSRF-TOKEN` HttpOnly e `Cache-Control: no-store`. O token do JSON é mascarado. |
| `POST /refresh` | Sem JSON; cookie `refresh_token`, cookie `AUTH-XSRF-TOKEN` e header `X-CSRF-TOKEN` com o campo `token` do JSON. | 200 sem corpo; novos `access_token` e `refresh_token`, com rotação e `Cache-Control: no-store`. |
| Rejeição CSRF | Cookie/header ausente ou incompatível quando o POST contém `refresh_token`. | 403 antes da rotação/revogação. A documentação não define código JSON obrigatório para esse erro; não inventar um. |
| Rejeição do refresh | CSRF aceito, refresh inválido/expirado/revogado/reutilizado. | 401 com `invalid_refresh_token`; manter encerramento definitivo existente. |
| `POST /login` | Email, password e `platform=mobile`. | Sem etapa CSRF; mantém 200 vazio e os dois cookies HttpOnly. |

O filtro exige CSRF em `POST /refresh` e `POST /logout` quando existe cookie chamado `refresh_token`. Isso não autoriza omitir refresh para contornar a proteção. CSRF da Core usa `/api/csrf`, `XSRF-TOKEN` e `X-XSRF-TOKEN`, que não são intercambiáveis com os da Auth.

O DTO de CSRF deve corresponder aos nomes reais `headerName` e `token` (camelCase). Validar header esperado `X-CSRF-TOKEN` e token não vazio; não transformar um nome arbitrário retornado em header sensível. Nunca copiar o valor do cookie para o header.

### Sequência proposta do Mobile

1. Respeitar o bloqueio existente de `Retry-After` e verificar refresh elegível para a rota de destino. Sem refresh ou com refresh expirado, preservar o comportamento existente sem consultar CSRF nem enviar POST.
2. Criar contexto privado da tentativa, associado à geração e à captura de refresh. Fazer `GET /csrf` na origem Auth, sem Bearer, access cookie ou refresh cookie e sem reaproveitar par anterior.
3. Capturar o cookie CSRF e o token JSON da mesma resposta. Validar status, payload e cookie elegível para `/refresh`; não prosseguir com par ausente, removido, expirado, ambíguo ou incompatível com o escopo configurado.
4. Revalidar geração e elegibilidade/validade do refresh depois do GET. Se o refresh expirou durante a preparação, não enviar o POST. Se a geração mudou, descartar a preparação; não iniciar um POST já conhecido como obsoleto.
5. No mesmo contexto de transporte, enviar um `POST /refresh` com os dois cookies e o header correto. Nunca enviar access cookie nem Bearer para essa rota.
6. Em sucesso, persistir o sucessor antes de publicar a sessão, seguindo o commit existente por geração. Uma mudança concorrente depois da verificação anterior não permite que a resposta antiga altere a nova sessão.
7. Descartar o par CSRF ao terminar/cancelar a tentativa. Uma próxima tentativa obtém outro par. Não criar timers, cache global ou armazenamento persistente de CSRF.

O ciclo inteiro GET + POST deve pertencer ao refresh já compartilhado por geração: chamadas protegidas concorrentes não criam vários pares nem vários POSTs. Manter a recuperação de 401 da Core com no máximo uma repetição da operação protegida após renovação confirmada.

Essa sequência é a estratégia cliente desta entrega, fundamentada no contrato; a API não exige consultar CSRF a cada chamada. A recuperação e as rotas foram confirmadas pelo solicitante em O-01/O-02 em 2026-10-19. Usar `/csrf` e `/refresh` na raiz da mesma origem Auth via HTTPS, sem prefixo público, preservando os atributos compatíveis confirmados, sem ampliar escopo de cookies.

### Custódia e isolamento

- Manter CSRF apenas em memória na tentativa, separado do conjunto submetido ao commit persistente de access/refresh. O servidor configura esse cookie sem Max-Age explícito; aceitar cookie de sessão, sem exigir `persistent=true`, sem fabricar TTL nem persistir `expiresAt` artificial.
- Preservar Domain, Path, hostOnly, Secure e HttpOnly; aplicar origem exata (scheme/host/port), `Cookie.matches` e verificação de expiração. Não ampliar escopo para viabilizar envio.
- Receber CSRF somente do endpoint/origem aprovados; enviar o cookie/header somente ao POST Auth autorizado. Cookies desconhecidos não entram na custódia por serem persistentes.
- Access continua só em memória e refresh continua no store criptografado existente. Não é necessária migração de formato por causa de CSRF.
- Login, Core, chatbot, outras origens/portas e redirects não recebem o par CSRF da Auth. Manter redirects e retries de conexão desabilitados.
- Não expor token/cookie CSRF, refresh, access, headers Cookie/Set-Cookie ou corpos sensíveis em domínio, UI, logs, exceções ou relatórios. Diagnósticos podem conter etapa, status e categoria sanitizados.
- Cancelar/fechar chamadas e corpos conforme os caminhos de término; falha/cancelamento da preparação não se transforma em falha de armazenamento. Preservar as garantias de commit após rotação e cancelamento cooperativo existentes.

### Falhas e recuperação aprovadas — O-01 resolvido

| Situação | Resultado aprovado |
| --- | --- |
| Rede/timeout no GET | Não enviar POST; preservar refresh ainda válido; usar categoria existente de rede/timeout. |
| 429 no GET ou POST | Não repetir automaticamente; preservar credencial elegível, respeitar `Retry-After` e mensagem existente de limite. Vencer o prazo apenas reabilita retry. |
| 5xx no GET ou POST | Não repetir automaticamente; preservar refresh elegível e informar indisponibilidade. |
| GET com JSON/cookie inválido, status inesperado ou POST 403 | Falha de protocolo, sem revogação local apenas por essa classificação; não declarar sessão ativa nem acessar conteúdo protegido. Descartar CSRF. Na restauração e durante o uso, apresentar “Não foi possível renovar sua sessão. Tente novamente.” com botão “Tentar novamente”, antes de liberar conteúdo protegido. O botão inicia uma nova tentativa com GET /csrf; não há repetição automática. |
| POST 401 `invalid_refresh_token` | Encerramento definitivo e limpeza conforme fluxo existente. Um 401 do GET público não prova invalidez do refresh. |
| Falha real de persistência após rotação ou sucesso sem par access/refresh válido | Manter falha local/bloqueio existentes; não generalizar a preservação de falhas CSRF para instalação parcial. |

Preservar refresh não significa ignorar expiração ou `Set-Cookie` válido de remoção/substituição. Continuar aplicando alterações elegíveis de cookies de autenticação por geração, inclusive em erro, conforme a spec de custódia. A resposta de preparação CSRF não deve poder instalar uma sessão ativa; eventuais cookies de autenticação inesperados nesse endpoint exigem tratamento explícito nos testes de isolamento.

## Acceptance criteria and evidence

Critérios com decisões O-01/O-02 resolvidas pelo solicitante em 2026-10-19. Para falha CSRF/protocolo, AC-06 e AC-14 exigem a mensagem e o botão definidos acima, sem conteúdo protegido ou repetição automática, tanto na restauração quanto durante uso ativo.

| ID | Observable given / when / then | Planned test or check |
| --- | --- | --- |
| AC-01 | Dado refresh elegível, quando renova, então GET CSRF precede um POST com ambos os cookies e token JSON no header, sem corpo JSON, access ou Bearer. | MockWebServer em `RetrofitAuthRepositoryTests`; token JSON distinto do cookie. |
| AC-02 | Dado par incompleto, payload inválido/header inesperado ou cookie incompatível, quando prepara, então nenhum POST é enviado e nenhum refresh válido é descartado só por isso. | Testes parametrizados de transporte/custódia e `SessionUseCaseTests`. |
| AC-03 | Dado CSRF de sessão sem Max-Age, quando prepara, então é enviado corretamente e não entra no store; após recriação, outro GET é necessário. | Store falso com inspeção de gravações; recriação de custody/repository/session. |
| AC-04 | Dado restore com refresh persistido, quando renova, então mantém expiração/scope originais e persiste apenas o sucessor após GET + POST bem-sucedidos. | Adaptação de `reopeningRestoresRefreshCookieAndRenewsSessionAtCurrentRoute`; instrumentado de store existente se afetado. |
| AC-05 | Dado refresh ausente/expirado, bloqueio por 429 ou expiração durante GET, quando tenta renovar, então não envia credenciais inelegíveis nem POST indevido. | Relógio falso e contagem de chamadas; testes de sessão para estados existentes. |
| AC-06 | Dadas falhas de rede/timeout/429/5xx na preparação, quando tratadas, então não há POST e segue preservação/recuperação aprovadas; 403 no POST não vira credencial inválida. | Transporte + sessão; contagem de chamadas e retry manual conforme O-01. |
| AC-07 | Dado 401 `invalid_refresh_token` no POST, quando tratado, então encerra a sessão; falhas locais de commit continuam bloqueando uso parcial. | Regressões de transporte, sessão e armazenamento com falha injetada. |
| AC-08 | Dadas operações concorrentes ou 401 atrasado, quando renovam, então compartilham GET + POST e revisão; a Core repete a operação protegida no máximo uma vez. | `SessionUseCaseTests` e `CoreSessionInterceptorTests`, com barreiras determinísticas. |
| AC-09 | Dado novo login durante GET/POST antigos, quando respondem, então não há instalação/limpeza de credenciais novas nem novo POST conhecido como obsoleto após GET. | MockWebServer com barreiras, verificação de memória/store e geração. |
| AC-10 | Dadas outras origens/portas/rotas, redirects, login, Core ou chatbot, quando chamados, então não recebem CSRF/refresh da tentativa. | Transporte com duas origens; testes de escopo, redirects e DI. |
| AC-11 | Dado cancelamento ou erro em cada etapa, quando termina, então recursos são liberados, preparação não revoga refresh e commit após rotação mantém as garantias existentes. | Testes de cancelamento/falhas e regressões de commit; revisão manual de fechamento justificada por não se inferir vazamento somente da resposta HTTP. |
| AC-12 | Dado diagnóstico, quando falha ou sucede, então contém apenas campos sanitizados; domain/presentation não conhecem o par CSRF. | `AuthTransportDiagnosticsTests` e revisão de imports/modelos/logs com dados sintéticos. |
| AC-13 | Dado ambiente configurado conforme O-02, quando realiza login e depois restore/refresh, então o serviço aceita o par e o app volta ao estado ativo. | Smoke manual em ambiente de teste e dispositivo, necessário para validar TLS, gateway e atributos reais; nenhuma credencial em evidências. |
| AC-14 | Dada falha CSRF/protocolo em restauração ou uso ativo, quando apresentada, então o usuário recebe mensagem/ação aprovadas sem conteúdo protegido ou loop automático. | Testes de ViewModel/navegação e verificação em dispositivo conforme resolução de O-01. |

## Technical impact

- `data/remote/auth/AuthApi.kt` e DTO específico: declarar CSRF e header explícito do refresh.
- `AuthCookieCustody.kt` / `StagedAuthCookieJar`: contexto de preparação/POST e separação de CSRF efêmero do commit de credenciais; não simplesmente incluir CSRF na lista que exige persistência.
- `RetrofitAuthRepository.kt`: coordenar GET + POST, classificar cada etapa, propagar cancelamento e evitar limpeza por erro remoto de protocolo. Generalizar a leitura de `Retry-After` para respostas com corpo de tipos diferentes, se necessário.
- `domain/model/AuthResult.kt`, `SessionUseCase.kt` e estados/apresentação estritamente necessários: distinguir falha de protocolo de falha local conforme O-01, sem expor detalhes HTTP ou segredos ao domínio.
- `app/di/RetrofitModule.kt`: manter isolamento de cliente/jar e serialização; ajustar wiring apenas se necessário. Qualificadores SDK/chatbot fora do escopo.
- Atualizar README e spec de custódia na implementação para substituir a regra “somente refresh cookie” pelo envio autorizado de refresh + CSRF. Esta spec delimita a exceção; não amplia permissões gerais do jar.
- Ambiente: testes JVM usam somente fixtures sintéticas; integração real usa configuração aprovada por O-02. O POST rotaciona registros de refresh no MongoDB via DS_Auth. Não alterar dados de negócio, provisionar contas ou registrar endpoints privados nesta spec.
- Risco: uma nova ida à rede aumenta a duração da renovação. Manter os limites HTTP existentes, conforme O-01 aprovado; não adicionar repetição automática ou novo timeout nesta correção.

### Plano de verificação após desbloqueio

Seguir Red–Green–Refactor: primeiro reproduzir ausência do GET/par, preservação em 403 e isolamento com testes comportamentais; implementar somente após falhas esperadas observadas; depois executar regressões relevantes.

Executar `testDebugUnitTest`, `spotlessCheck`, `detekt`, `lintDebug` e `assembleRelease`; `connectedDebugAndroidTest` quando houver dispositivo/emulador. Reportar cada execução e limitação. O gate CI combinado continua 80% de linhas e 70% de branches; testes JVM locais não demonstram essa cobertura combinada.

## Decisions and open questions

**Bloqueio obrigatório por instrução do solicitante:** não implementar código, testes ou configuração enquanto existir qualquer item Open nesta spec, inclusive novos itens descobertos. Para cada resolução registrar decisão, fonte/responsável e data, atualizar fluxo/ACs e somente então mudar o status para `Ready for implementation`. Ausência de resposta não é aprovação. Este bloqueio não impede editar a própria spec para resolver decisões.

| Item | Status | Source or decision owner |
| --- | --- | --- |
| D-01 — Cookie/header/rota e token mascarado do JSON; 403 antes da rotação; 401 do POST com invalid_refresh_token é rejeição definitiva. | Decidido | README/plano e código DS_Auth, consultados em 2026-10-19. |
| D-02 — Login não exige CSRF; Core mantém Bearer e seu par CSRF é separado. | Decidido | DS_Auth README/plano e arquitetura Mobile, 2026-10-19. |
| D-03 — Sem implementação enquanto houver pontos Open. | Decidido | Solicitante, 2026-10-19. |
| D-04 — Base não contém logout remoto; esta correção cobre refresh/restauração. | Delimitação desta spec | Inspeção do workspace e análise anterior apresentada ao solicitante, 2026-10-19. Logout requer escopo adicional explícito. |
| O-01 — Preservar refresh elegível e bloquear operações/conteúdo protegidos em falha CSRF/protocolo. Na restauração e durante uso, mostrar “Não foi possível renovar sua sessão. Tente novamente.” com botão “Tentar novamente”. Retry manual começa por novo GET /csrf; nenhum retry automático. Manter limites HTTP atuais. | Decidido | Solicitante, resposta interativa “Aprovar comportamento proposto”, 2026-10-19. |
| O-02 — `/csrf` e `/refresh` disponíveis na raiz da mesma origem Auth via HTTPS, sem prefixo público; Domain/Path compatíveis com origem e `/refresh`, Secure e HttpOnly confirmados. Preservar restrições existentes sem ampliar cookies; gateway com prefixo fora de escopo. | Decidido | Solicitante, resposta interativa “Confirmo rotas e atributos compatíveis”, 2026-10-19. Confirmação de configuração, não evidência de smoke executado. |

Não há decisões em aberto nesta revisão. Se surgir nova dúvida de produto ou contrato durante a execução, registrá-la como Open e interromper a implementação até sua resolução.

## Implementation outcome

Somente spec preparada em 2026-10-19. Nenhum código, teste ou configuração implementado. Nenhum teste Gradle, instrumentado ou smoke executado. Evidência atual: leitura do workspace e das fontes locais DS_Auth. Critérios planejados não são critérios comprovados.

Spec atualizada para Ready for implementation após as duas respostas do solicitante em 2026-10-19. A resolução das decisões não comprova testes ou integração real; implementação ainda não iniciada.
