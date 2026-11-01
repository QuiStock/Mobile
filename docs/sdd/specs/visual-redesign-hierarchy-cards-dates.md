# [QUIS-VISUAL-04] Hierarquia Visual de Cards, Detalhes de Confirmação, Pop-ups, Formatação de Data Dia-Mês-Ano e Acesso Universal ao Perfil

**Status:** Completed  
**Issue:** feat/visual-changes  
**Product decision owner:** Isabella Camargo / QuiStock Team

## Problem and goal

A usuária identificou pontos de melhoria na experiência e hierarquia visual nas telas de criação e confirmação:
1. **Tela de confirmação de envio (Anexo 1):** Os detalhes da aprovação aparecem como texto corrido e solto em um único bloco, sem hierarquia entre as informações, e o conteúdo está encostado no topo, deixando a tela desbalanceada. O objetivo é centralizar o conteúdo verticalmente e criar uma hierarquia visual elegante para os dados (título, produto, status, valores e datas).
2. **Card de seleção de produto e botão voltar (Anexo 2):** O card de produto lista as informações de forma crua (com timestamp ISO longo). É necessário distribuir melhor as informações com hierarquia (nome em destaque, categoria/estoque em badge/subtítulo, e data formatada). O botão "Voltar" deve retornar ao design original do app (`‹ Voltar` / seta com texto, sem formato de pílula sólida/secundária).
3. **Card do produto selecionado e formatação de data (Anexo 3):** No formulário de criação, o card do produto selecionado deve seguir a mesma hierarquia e a data nos campos de entrada deve ser formatada como dia-mês-ano (`DD-MM-AAAA`).
4. **Pop-up de confirmação de envio (Anexo 4):** O diálogo de revisão apresenta as informações como bloco único centralizado. O objetivo é criar hierarquia visual clara (título da ação, produto em destaque, e detalhes como desconto/datas ou quantidade/previsão organizados em linhas chave-valor).
5. **Acesso ao Perfil em Todas as Telas:** O perfil deve poder ser acessado ao tocar nos elementos de cabeçalho do usuário (foto de perfil, loja e nome) a partir de qualquer tela da aplicação.

## Scope

**In scope:**
- Centralização vertical do conteúdo de confirmação de envio em `fragment_creation.xml`.
- Estruturação hierárquica dos detalhes da ação confirmada (tipo de ação, produto, estado, desconto/preço ou quantidade, e datas formatadas como dia-mês-ano).
- Reversão do botão "Voltar" em `fragment_creation.xml` para o design padrão do aplicativo (`arrow_right` rotacionado + texto "Voltar" sem fundo de pílula).
- Formatação dos cards de produto em `CreationPresentation.kt` e `CreationFragment.kt` com hierarquia tipográfica e data da análise em `dia-mês-ano`.
- Suporte a datas no formato `dia-mês-ano` (`DD-MM-AAAA` e `DD/MM/AAAA`) no `ActionDraft`, no seletor de data (`showDatePicker`), no placeholder/hint e mapeamento para ISO `YYYY-MM-DD` nas chamadas de rede da API.
- Estruturação do pop-up `dialog_confirmation_popup.xml` e método `showConfirmationDialog` para exibir hierarquia limpa entre título, produto e linhas chave-valor com datas em dia-mês-ano.
- Navegação universal ao perfil em `ProfileBindingCallbacks.kt` configurando clique nos elementos de identificação do usuário em todas as telas.
- Inclusão do avatar de perfil nos cabeçalhos de `fragment_creation.xml` e `fragment_creation_hub.xml`.

**Out of scope:**
- Alterações em regras de negócio, fluxos de validação de elegibilidade ou endpoints da API.

## Expected behavior

- **Tela de Confirmação:** Ao enviar uma interferência, o ícone de sucesso, mensagens, card detalhado e botão de retorno aparecem centralizados na tela. O card apresenta linhas estilizadas com rótulos e valores destacados, e datas no formato `DD-MM-AAAA`.
- **Botão Voltar:** Exibido como seta para a esquerda e o texto "Voltar", alinhado à esquerda no topo da área de conteúdo.
- **Card de Produto:** O nome do produto aparece em negrito e tamanho maior, categoria e estoque em linha secundária limpa, e data de análise formatada amigavelmente como `DD-MM-AAAA`.
- **Datas:** Ao selecionar uma data no calendário, o campo é preenchido no formato `DD-MM-AAAA`. O formulário aceita o formato e valida corretamente; o envio à API converte para o formato ISO esperado pelo servidor.
- **Pop-up de Revisão:** Apresenta o tipo da ação ("Promoção" / "Pedido de compra") em destaque no topo, o nome do produto e os valores em linhas alinhadas com rótulos secundários e valores principais em negrito.
- **Acesso ao Perfil:** O toque no avatar ou identificação do usuário no cabeçalho navega diretamente para o `profileFragment`.

## Acceptance criteria

| ID | Observable given / when / then | Planned test or check | Status |
| --- | --- | --- | --- |
| AC-01 | Dado o envio de uma interferência, a tela de confirmação exibe o conteúdo centralizado verticalmente e os detalhes estruturados com hierarquia visual e datas em `DD-MM-AAAA`. | Inspeção de layout XML e renderização em `CreationFragment` | Pass |
| AC-02 | Dado o fluxo de criação, o botão "Voltar" no topo possui o design de seta `arrow_right` com texto "Voltar" sem formato de pílula. | Inspeção visual de layout em `fragment_creation.xml` | Pass |
| AC-03 | Dado a listagem ou seleção de produtos, o card exibe hierarquia clara entre nome, categoria/estoque e data de análise formatada como dia-mês-ano. | Verificação de `CreationPresentation` e `CreationFragment` | Pass |
| AC-04 | Dado o campo de data de pedido ou promoção, a seleção via calendário e a digitação exibem a data no formato `DD-MM-AAAA` e são validadas com sucesso. | Testes unitários de `ActionDraft` e `CreationFields` | Pass |
| AC-05 | Dado o clique em "Revisar e enviar", o pop-up exibe título da ação, produto e detalhes com rótulos secundários e valores em negrito. | Inspeção de `dialog_confirmation_popup.xml` e testes de diálogo | Pass |
| AC-06 | Dado o toque no avatar ou cabeçalho de perfil em qualquer tela, o aplicativo navega para `profileFragment`. | Verificação em `ProfileBindingCallbacks` | Pass |
| AC-07 | Todos os testes de unidade, qualidade e compilação passam (`testDebugUnitTest`, `spotlessCheck`, `detekt`). | Comandos Gradle de verificação | Pass |
