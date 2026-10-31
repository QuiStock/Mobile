# Integração do chatbot com a Core

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

## Contratos e limitações

O cliente usa a sessão e o transporte autenticado da Core. A Core encaminha mensagens e operações de histórico/encerramento à IA. Dados do card são contexto, não autorização. A conversa é identificada localmente por conta; a recuperação em outro aparelho está fora do escopo.

Os testes focados do chatbot, Spotless, Detekt, lintDebug e assembleDebug passaram localmente; os testes instrumentados compilaram, mas não foram executados em aparelho. A suíte unitária completa da versão final foi interrompida e o assembleRelease não foi concluído. Validar a integração completa após implantar a Core e registrar o resultado por conta, sem expor credenciais ou dados pessoais.
