# [QUIS-VISUAL-03] Correção Visual das Telas de Ajuda, Botões de Criação, Pop-ups Customizados e Tela de Confirmação

**Status:** Implemented and Verified  
**Issue:** feat/visual-changes  
**Product decision owner:** Isabella Camargo / QuiStock Team

## Problem and goal

Usuária reportou quatro problemas visuais no aplicativo:
1. Nas 2 telas de ajuda ("Entre em contato" e "FAQ"), os textos e títulos estavam com a cor branca hardcoded sobre fundo claro (`#F9F7FA`), tornando a leitura impossível no tema claro.
2. Nas telas de criação de interferências (Pedido de Compra e Promoção), os botões secundários (`creationBack`, `creationRetry`, `creationReturn`) apareciam como retângulos roxos sólidos sem texto visível, devido ao `backgroundTint` padrão do MaterialButton sobrepondo o fundo personalizado e misturando texto roxo sobre fundo roxo.
3. As caixas de diálogo de confirmação (`showReview`, descarte e encaminhamento) estavam utilizando o `AlertDialog` nativo cinza padrão do Android em vez do pop-up personalizado desenvolvido no projeto (`dialog_confirmation_popup.xml`).
4. Ao concluir o envio de uma interferência, a tela não exibia o layout visual de confirmação criado pela usuária (`fragment_order_sent.xml` e `fragment_promo_sent.xml`), apresentando apenas o resumo cru do formulário com um botão genérico.

O objetivo é corrigir e padronizar visualmente esses quatro pontos sem alterar a lógica de negócios existente.

## Scope

**In scope:**
- Atualização de `fragment_contact.xml` e `fragment_faq.xml` para aplicar tokens de cores semânticos (`@color/color_text_title`, `@color/color_text_subtitle`, `@color/color_icon`, etc.) garantindo legibilidade no modo claro e escuro.
- Correção de `bg_input_outline.xml` e `bg_popup_dialog.xml` para responderem às paletas de tema claro e escuro.
- Ajuste dos botões secundários em `fragment_creation.xml` com `app:backgroundTint="@null"` e tipografia correta, garantindo que "Voltar", "Atualizar consulta" e "Voltar para Interferências" apareçam claramente com suas bordas e textos visíveis.
- Substituição dos diálogos genéricos do `CreationFragment` pela renderização do pop-up personalizado `dialog_confirmation_popup.xml` estilizado, preservando o suporte a testes automatizados.
- Implementação visual da tela de confirmação de envio (`CreationPhase.SENT`) em `fragment_creation.xml`, incorporando o ícone `@drawable/confirmation`, título e subtítulo de confirmação, card estilizado com os dados e o botão "Voltar para Interferências".
- Atualização das telas `fragment_order_sent.xml`, `fragment_promo_sent.xml`, `fragment_manager_order_approved.xml` e `fragment_manager_promo_approved.xml` com cores semânticas.

**Out of scope:**
- Qualquer alteração na lógica de negócio de submissão, validação de campos ou repositórios.
- Alteração nos contratos de API ou navegação de rotas.

## Expected behavior

- Nas telas de Ajuda ("Entre em contato" e "FAQ"), todos os textos, títulos, campos e setas são perfeitamente legíveis tanto no modo claro quanto no modo escuro.
- Nas telas de Criação, os botões exibem textos legíveis: o botão "Voltar" no topo possui texto claro e formato secundário com borda suave; o botão "Atualizar consulta" exibe texto visível; as ações primárias destacam-se em violeta com texto branco.
- Ao clicar em "Revisar e enviar", os detalhes da ação aparecem em um diálogo arredondado com `@drawable/bg_popup_dialog`, fundo correspondente ao tema, título legível e botões estilizados "Confirmar" e "Cancelar".
- Ao enviar uma interferência com sucesso, é apresentada a tela de confirmação personalizada com o selo verde de confirmação, mensagem de sucesso e botão para retornar às interferências.

## Acceptance criteria and evidence

| ID | Observable given / when / then | Planned test or check | Status |
| --- | --- | --- | --- |
| AC-01 | Dado o app em modo claro, quando o usuário abre as telas "Entre em contato" e "FAQ", então todos os títulos, perguntas e campos são legíveis com cor de texto escura. | Inspeção visual de layout XML e compilação | Verificado |
| AC-02 | Dado o formulário de criação em modo claro ou escuro, os botões secundários ("Voltar", "Atualizar consulta") exibem rótulos visíveis e contraste correto. | Inspeção de layout XML e compilação | Verificado |
| AC-03 | Dado que o usuário revisa ou descarta um formulário de criação, o pop-up de confirmação exibido utiliza o layout personalizado `dialog_confirmation_popup.xml`. | Verificação em `CreationFragment` e execução de testes em `OrderAndPromotionFragmentTests` | Verificado |
| AC-04 | Dado que o usuário conclui o envio de um pedido ou promoção, a tela exibe o visual de confirmação com `@drawable/confirmation` e botão de retorno para interferências. | Verificação de layout e compilação | Verificado |
| AC-05 | Todos os testes de unidade e qualidade passam (`testDebugUnitTest`, `spotlessCheck`, `detekt`, `lintDebug`, `assembleRelease`). | Comandos Gradle de verificação | Verificado |

## Implementation outcome

Todas as verificações foram executadas e aprovadas com sucesso no Gradle:
1. `spotlessApply` e `spotlessCheck`: formatação de código aprovada.
2. `detekt`: 0 erros de estilo ou complexidade estática.
3. `testDebugUnitTest`: 100% dos testes unitários passaram.
4. `lintDebug`: análise do Android Lint aprovada sem erros bloqueantes.
5. `assembleRelease`: compilação e empacotamento de release concluídos com sucesso.

## Technical impact

- Arquivos afetados:
  - `app/src/main/res/drawable/bg_input_outline.xml`
  - `app/src/main/res/drawable/bg_popup_dialog.xml`
  - `app/src/main/res/layout/fragment_contact.xml`
  - `app/src/main/res/layout/fragment_faq.xml`
  - `app/src/main/res/layout/dialog_confirmation_popup.xml`
  - `app/src/main/res/layout/fragment_creation.xml`
  - `app/src/main/res/layout/fragment_order_sent.xml`
  - `app/src/main/res/layout/fragment_promo_sent.xml`
  - `app/src/main/res/layout/fragment_manager_order_approved.xml`
  - `app/src/main/res/layout/fragment_manager_promo_approved.xml`
  - `app/src/main/java/com/quistock/quistock/presentation/interferences/CreationFragment.kt`
  - `docs/sdd/specs/visual-redesign-help-popups-confirmation.md`
