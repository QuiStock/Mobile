# [QUIS-UI-REDESIGN] Reformulação Visual da Home e Chatbot Inspirada nos Mockups

**Status:** Completed  
**Issue:** feat/visual-changes  
**Product decision owner:** Isabella Camargo / QuiStock Team

## Problem and goal

A interface anterior apresentava limitações visuais:
1. Na tela Home, os três cards de resumo estavam espremidos lado a lado em colunas estreitas, quebrando palavras de forma inadequada ("Venci / • mento / curto").
2. No Chatbot, a tela inicial não continha o estado de boas-vindas com o mascote Fefo, a barra de entrada era simples e as mensagens usavam balões rígidos com largura fixa que não se assemelhavam a aplicativos modernos de mensagens.

O objetivo desta mudança foi:
- Reformular a tela Home conforme o anexo 1, destacando o card de "Ações ativas" à esquerda e organizando os cards de "Vencimento curto" e "Ruptura e excesso" empilhados à direita com distribuição de texto limpa e sem hifenação incorreta.
- Reformular a tela do Chatbot conforme o anexo 2 (estado de boas-vindas com mascote Fefo, botões circulares no topo e campo de entrada em pílula com botão circular de envio).
- Reformular os balões de conversa do Chatbot conforme o anexo 3 e aplicativos modernos de mensagens (balão do usuário em violeta com cantos arredondados assimétricos à direita, balão do bot em superfície suave à esquerda, largura dinâmica ajustada ao conteúdo).

## Scope

**In scope:**
- Layout de resumo da Home (`fragment_home.xml`): card de "Ações ativas" em destaque à esquerda e cards de "Vencimento" e "Ruptura" empilhados à direita com bullets coloridos e tipografia estável sem quebras de palavras no meio de termos.
- Superfícies dos cards de alertas e avisos atualizadas para o padrão de superfície sólida com cantos arredondados (`18-20dp`).
- Layout do Chatbot (`fragment_chatbot.xml`):
  - Botões circulares de cabeçalho (Menu e Notificações).
  - Estado vazio/boas-vindas com mascote Fefo (`ic_fefo.png`), título "Boas-vindas" e texto de apoio.
  - Barra de entrada em pílula (`bg_chat_input_pill.xml`) com botão circular de envio (`bg_circle_send.xml` e ícone de seta `ic_arrow_up.xml`).
- Drawables de balões de mensagens:
  - Balão do usuário (`bg_chat_bubble_user.xml`): violeta com cantos arredondados assimétricos (`18dp`, cauda `4dp` no topo direito).
  - Balão do bot (`bg_chat_bubble_bot.xml`): superfície neutra com cantos arredondados assimétricos (`18dp`, cauda `4dp` no topo esquerdo).
- Atualização em `ChatbotFragment.kt` para gerenciar a exibição do estado de boas-vindas e inflar os novos balões com largura fluida (wrap content com limite máximo de 78%).
- Adequação das cores e estilos nas outras telas do app (`bg_card_summary.xml`, `bg_card_interference_category.xml`, substituição de cores fixas por tokens semânticos).

**Out of scope:**
- Alterações nos contratos de rede ou regras de negócio do chatbot.
- Redesenho de fluxos de login ou cadastro pessoal.

## Expected behavior

- Na Home, o usuário visualiza "Ações ativas" como o card principal à esquerda e os outros dois indicadores à direita com leitura clara e espaçamento agradável.
- Ao entrar no Chatbot sem mensagens, o usuário vê a tela de boas-vindas com o Fefo centralizado e o convite para perguntar sobre estoque.
- Ao enviar uma mensagem, o estado de boas-vindas dá lugar à lista de mensagens.
- As mensagens do usuário aparecem alinhadas à direita em balões violetas com texto branco.
- As respostas do bot aparecem alinhadas à esquerda em balões de superfície elegante com boa legibilidade.
- O campo de texto fica contido na pílula moderna com o botão circular roxo de envio.

## Acceptance criteria and evidence

| ID | Observable given / when / then | Planned test or check | Status |
| --- | --- | --- | --- |
| AC-01 | Na Home, os indicadores numéricos estão distribuídos com "Ações ativas" à esquerda e "Vencimento curto" e "Ruptura e excesso" empilhados à direita sem corte de palavras. | Verificação de layout e build | APROVADO |
| AC-02 | No Chatbot vazio, o layout exibe o mascote Fefo, título "Boas-vindas" e subtítulo explicativo. | Verificação de layout e teste de fragmento | APROVADO |
| AC-03 | Mensagens do usuário usam balão estilizado à direita e respostas do assistente usam balão estilizado à esquerda. | Verificação de `ChatbotFragment.kt` e build | APROVADO |
| AC-04 | A barra de digitação possui formato de pílula com botão circular de envio. | Verificação de `fragment_chatbot.xml` | APROVADO |
| AC-05 | Todos os testes unitários e verificações de qualidade passam sem regressão. | `.\gradlew.bat testDebugUnitTest`, `spotlessCheck`, `detekt`, `lintDebug`, `assembleRelease` | APROVADO |

## Technical impact

- Arquivos modificados/adicionados:
  - `docs/sdd/specs/visual-redesign-home-chatbot.md`
  - `app/src/main/res/drawable/bg_circle_button.xml`
  - `app/src/main/res/drawable/bg_circle_send.xml`
  - `app/src/main/res/drawable/bg_chat_input_pill.xml`
  - `app/src/main/res/drawable/bg_chat_bubble_user.xml`
  - `app/src/main/res/drawable/bg_chat_bubble_bot.xml`
  - `app/src/main/res/drawable/bg_card_summary_item.xml`
  - `app/src/main/res/drawable/bg_dot_yellow.xml`
  - `app/src/main/res/drawable/bg_dot_pink.xml`
  - `app/src/main/res/drawable/ic_arrow_up.xml`
  - `app/src/main/res/layout/fragment_home.xml`
  - `app/src/main/res/layout/fragment_chatbot.xml`
  - `app/src/main/java/com/quistock/quistock/presentation/chatbot/ChatbotFragment.kt`
  - `app/src/main/res/drawable/bg_card_summary.xml`
  - `app/src/main/res/drawable/bg_card_interference_category.xml`
  - Layouts de demais telas com padronização dos tokens de cor
