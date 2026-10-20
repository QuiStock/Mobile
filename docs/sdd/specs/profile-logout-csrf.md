# CSRF da Auth no logout do perfil

**Status:** Ready for implementation — sequência aprovada em 2026-10-20
**Issue:** none
**Product decision owner:** Solicitante
**Data:** 2026-10-20

## Problem and goal

Após integrar a base `51bcdb6`, refresh obtém CSRF, mas `AuthApi.logout()` ainda não recebe header e `prepareLogout()` envia apenas o refresh capturado. DS_Auth rejeita esse POST com 403 antes de revogar a família. Limpeza local não comprova logout remoto.

Fontes locais: [README DS_Auth](../../../../DS_Auth/README.md), seção “CSRF for refresh/logout”; [plano de autenticação](../../../../DS_Auth/docs/authentication-api-plan.md), contrato de logout; [SecurityConfiguration](../../../../DS_Auth/src/main/java/com/quistock/auth/config/SecurityConfiguration.java), `requiresCsrf`; [CsrfController](../../../../DS_Auth/src/main/java/com/quistock/auth/controller/CsrfController.java). O código confirma rotas na raiz e CSRF condicional à presença de `refresh_token`. A frase histórica do README sobre rotas antigas não altera esse contrato corroborado pelo código e pela confirmação anterior do solicitante.

Esta extensão complementa [logout do perfil](profile-logout.md) e [CSRF no refresh](auth-refresh-csrf.md). Não constitui evidência do ambiente remoto.

## Scope

**In scope:** par CSRF privado por tentativa de logout, prazo total, isolamento, falhas, diagnósticos e regressões da integração.

**Out of scope:** alterar DS_Auth/Core, persistir CSRF, alterar textos/recuperação local, repetir revogação, mudar gates ou suportar prefixos públicos.

## Expected behavior

Contrato confirmado: `GET /csrf` retorna JSON `headerName`/`token` e cookie HttpOnly `AUTH-XSRF-TOKEN`. O token mascarado do JSON vai em `X-CSRF-TOKEN`; nunca copiar o valor do cookie para o header. `POST /logout` com refresh exige esse par, sem JSON, access cookie ou Bearer. O par da Core não é intercambiável. 403 não confirma revogação; 204 confirma a operação inclusive para refresh desconhecido/já revogado. Sem refresh não há família identificável.

Sequência aprovada pelo solicitante em 2026-10-20:

1. Manter confirmação, bloqueio imediato, captura privada do refresh elegível, avanço da geração e limpeza local independente da rede.
2. Com refresh inicial elegível para `/logout`, fazer um GET `/csrf` sem credenciais em jar exclusivo da tentativa. Não reutilizar CSRF de refresh concorrente/anterior.
3. Validar status 200, header exatamente `X-CSRF-TOKEN`, token não vazio com caracteres válidos para header e um único cookie CSRF elegível para `/logout`. Preservar origem exata, Domain, Path, Secure, HttpOnly e validade; aceitar cookie de sessão sem persistir nem inventar TTL.
4. Revalidar refresh antes do POST. Não exigir `custody.isCurrent(generationAntiga)`: a limpeza avança essa geração deliberadamente. A captura é de uso único e nunca faz commit em custody. Respostas antigas não podem reinstalar credenciais nem afetar novo login.
5. Fazer no máximo um POST com refresh + CSRF cookie + header. Preparação inválida impede POST; não retirar refresh elegível para contornar CSRF. Se o refresh expirar durante GET, encerrar a tentativa como não confirmada, sem POST.
6. Sem refresh elegível desde o início, manter P-03: um POST sem cookies/header e sem GET, aceitando 204 sem afirmar revogação de família. Segue o filtro condicional do servidor.
7. Aplicar os 10 segundos aprovados ao ciclo remoto inteiro: prazo monotônico, somente tempo restante para a segunda chamada e cancelamento efetivo da chamada ativa no limite. Não usar apenas timeout de coroutine em torno de `Call.execute()` bloqueante. Limpeza local não participa desse prazo.
8. Falha de rede, timeout, payload/cookie inválido, 403, 429 ou status inesperado em qualquer etapa resulta em remoto não confirmado. Sem retries, inclusive HTTP follow-ups. Manter limpeza e aviso já aprovado; não reutilizar recuperação manual de refresh. Retry local repete somente limpeza.
9. Descartar CSRF e referências à captura no término, fechar corpos/cancelar chamadas. Nunca persistir CSRF ou guardar refresh para segunda tentativa. Morte do processo continua sem promessa de conclusão.
10. Logs: `operation=auth_logout`, etapa `csrf`/`logout`, status, duração e categoria sanitizada. Sem tokens, cookies, headers, corpos, identificadores ou URLs privadas.

## Acceptance criteria and evidence

Todos os testes abaixo são planejados; não foram escritos/executados nesta extensão.

| ID | Observable given / when / then | Planned test or check |
| --- | --- | --- |
| AC-01 | Com refresh elegível, GET precede um único POST; header usa token JSON distinto do cookie. | `LogoutTransportTests`, MockWebServer com fixtures distintas, método/corpo/headers. |
| AC-02 | GET sem credenciais; POST envia somente par Auth e refresh na rota/origem autorizada. | Jar com rotas, host, porta, Path, expiração e duas origens; redirects/follow-ups bloqueados. |
| AC-03 | Payload/header/cookie ausente, inválido, ambíguo ou incompatível impede POST; limpeza continua e aviso aparece. | Transporte parametrizado e `SessionLogoutTests`. |
| AC-04 | Sem refresh inicial, apenas POST sem credenciais; expiração durante GET impede POST e não confirma remoto. | Relógio controlado e contagem de chamadas. |
| AC-05 | GET + POST compartilham 10 segundos e cancelam chamada ativa sem retry. | Barreiras/atrasos em cada etapa e inspeção de cancelamento; não basta timeout por chamada. |
| AC-06 | Limpeza termina com GET pendente; avanço da geração não invalida captura de logout nem permite commit tardio. | Concorrência/store com barreiras; novo login permanece intacto. |
| AC-07 | 204 mantém sucesso; 403/429/5xx/offline mantêm aviso e recuperação exclusivamente local quando necessária. | Repositório/domínio e instrumentados de navegação/recriação. |
| AC-08 | CSRF nunca é persistido, reutilizado ou exposto em logs/domínio/UI. | Store falso, tentativas isoladas, logger capturado e revisão de fronteiras. |
| AC-09 | Refresh mantém recuperação manual; logout bloqueia conteúdo/Voltar sem sobrepor fluxos. | `SessionRecoveryTests`, `SessionLogoutNavigationTests` e instrumentados, inclusive refresh pendente ao sair. |
| AC-10 | Ambiente aceita par no logout; família não renova depois e outra família permanece válida. | Smoke controlado em dispositivo/ambiente de teste; mocks não comprovam revogação real. |

## Technical impact

Ordem proposta de commits em inglês, após aprovação:

1. `docs: approve Auth CSRF contract for profile logout`: encerrar O-01, atualizar AC-02/04/05/11 da spec original e README/contexto.
2. `feat(auth): protect logout with an isolated CSRF exchange`: header explícito opcional em `AuthApi.logout`; adaptar `StagedAuthCookieJar.hasCsrf` para a rota autorizada da tentativa; executor específico de logout com GET/POST/prazo/descarte e coordenação no repositório.
3. `test(auth): cover CSRF logout isolation and failure recovery`: adicionar/adaptar testes acima e registrar limitações. Quando execução for autorizada, aplicar Red–Green–Refactor junto da implementação, sem adiar descoberta de requisitos para esse commit.

O jar já captura CSRF separadamente, mas `hasCsrf()` está fixado em `/refresh`. O executor `AuthRequest` verifica geração corrente e comita cookies: reutilizá-lo inteiro quebra logout. Compartilhar apenas validação/transporte, mantendo isolamento da captura.

Não é necessária migração de store, novo módulo ou alteração de contrato de domínio. A segunda ida à rede consome o orçamento; falha continua sem confirmação remota. O POST afeta a família de refresh no DS_Auth, sem alterar dados de negócio.

## Decisions and open questions

| Item | Status | Source or decision owner |
| --- | --- | --- |
| D-01 — Nomes, token mascarado, CSRF condicional e 403 anterior à revogação. | Decidido | DS_Auth documentação/código, 2026-10-20. |
| D-02 — Limpeza independente, tentativa única, 10 segundos, aviso e retry apenas local. | Decidido | Aprovações anteriores da spec de logout. |
| O-01 — Aprovar GET somente com refresh inicial elegível, orçamento único GET + POST, interromper sem POST se refresh expirar durante GET e tratar falhas da preparação com aviso remoto atual. | Decidido | Solicitante, aprovação explícita em 2026-10-20. |

Enquanto O-01 ou nova dúvida genuína estiver Open, não implementar esta extensão. Resolução do rebase não representa aprovação desse plano.

## Implementation outcome

Rebase de `feat/logout` sobre `51bcdb6` concluído em 2026-10-20, preservando recuperação manual de refresh, bloqueio de follow-ups Auth, estados/diálogos de logout e refatorações de qualidade. Logout ainda não envia CSRF.

Nenhum teste ou comando Gradle executado. Revisão estática não comprova compilação, qualidade, integração real ou cumprimento dos ACs.
