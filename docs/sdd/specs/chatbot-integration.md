# Integração do chatbot com a Core

## Correção de instrumentação no API 37 — 04/11/2026

Na CI do PR #77, run `41903419816`, revisão `93fe353`, foram executados 123 testes
com uma falha: `ChatbotFragmentTests.emptyMessageShowsValidationError` aguardou a
Home até timeout. O logcat mostra `GrantPermissionsActivity` e a MainActivity em
PAUSED durante a espera. O runner concedia localização, mas não POST_NOTIFICATIONS,
solicitada no primeiro perfil Ready no Android 13+.

O runner passa a conceder notificações somente no API 33+, antes das Activities,
e verifica a concessão de todas as permissões de setup. Falhas de concessão agora
interrompem o setup com diagnóstico explícito. O teste continua exigindo Home e
Chatbot RESUMED, verifica o aviso de mensagem vazia e confirma zero envios ao
ViewModel. Não houve alteração de timeouts, critérios de prontidão, comportamento
de produção ou gates. O diálogo real de permissão continua sendo check manual.

Verificação desta correção: `git diff --check` passou. Compilação androidTest e
Spotless foram tentados, mas o Gradle não iniciou as tasks: acesso negado ao lock
do wrapper no sandbox e `Unable to establish loopback connection` com execução
ampliada. A nova execução da CI deve confirmar a aprovação; os sucessos do commit
anterior não validam esta revisão.

**Status:** Implementado localmente; validação de ponta a ponta pendente.
**Issue:** não informada.
**Product decision owner:** solicitante.

## Objetivo e escopo

Integrar a tela existente ao fluxo autenticado Mobile → Core → IA. A conversa ativa é retomada no mesmo aparelho; o menu mostra conversas encerradas e permite abri-las. Nova conversa encerra a atual antes de iniciar outra. Ajuda do FEFO fornece o contexto do alerta na conversa atual. Sincronização entre aparelhos não está incluída.

## Comportamento e critérios de aceite

| Critério | Comportamento esperado | Verificação |
| --- | --- | --- |
| AC-01 | Enviar e receber mensagens pela Core com sessão válida; exibir rejeições e falhas sem repetir automaticamente escritas. | `RetrofitChatbotRepositoryIntegrationTests`, `ChatbotViewModelTests` |
| AC-02 | Gerar e salvar o identificador da conversa por usuário antes do envio; recuperar mensagens ao reabrir no mesmo aparelho. | `ChatbotConversationTests`, `ChatbotViewModelTests`; confirmar em aparelho |
| AC-03 | Listar conversas encerradas, abrir histórico e encerrar a conversa atual antes de iniciar outra; verificar estado quando o encerramento for incerto. | `ChatbotViewModelTests`; confirmar em aparelho |
| AC-04 | Abrir Ajuda do FEFO no chatbot com nome e categoria do alerta, sem inventar ID ou data. | `ChatbotViewModelTests`; confirmar navegação no aparelho |
| AC-05 | Após envio incerto, oferecer reenvio manual com aviso de possível duplicação. Troca de sessão não exibe respostas anteriores. | `ChatbotViewModelTests` |
| AC-06 | Distinguir sessão ausente, perfil carregando e falha do perfil; bloquear chamadas do chatbot até perfil pronto, preservar rascunho e contexto FEFO quando o envio for recusado localmente, e nunca enviar automaticamente quando o perfil ficar pronto. | `ChatbotViewModelTests`, `ChatbotFragmentTests` |

## Contratos e limitações

O cliente usa a sessão e o transporte autenticado da Core. A Core encaminha mensagens e operações de histórico/encerramento à IA. Dados do card são contexto, não autorização. A conversa é identificada localmente por conta; a recuperação em outro aparelho está fora do escopo.

Os testes focados do chatbot, Spotless, Detekt, lintDebug e assembleDebug passaram localmente; os testes instrumentados compilaram, mas não foram executados em aparelho. A suíte unitária completa da versão final foi interrompida e o assembleRelease não foi concluído. Validar a integração completa após implantar a Core e registrar o resultado por conta, sem expor credenciais ou dados pessoais.

O envio só é aceito com sessão ativa e perfil Core pronto para a mesma geração e usuário. A tela mantém o texto digitado quando o ViewModel recusa o envio; a transição do perfil para pronto pode carregar o histórico, mas exige novo toque explícito para enviar o rascunho.
