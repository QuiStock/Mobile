# [QUIS-VISUAL-02] Adaptação Visual do Skeleton, Detalhe do Produto e Formulário de Criação (Modo Claro/Escuro e DatePicker)

**Status:** Implemented and Verified  
**Issue:** feat/visual-changes  
**Product decision owner:** Isabella Camargo / QuiStock Team

## Problem and goal

No modo claro recém-implementado:
1. O skeleton loading (`SkeletonLoadingScreen`) continuava utilizando tons pretos/roxos escuros hardcoded (`#11061F`), gerando uma tela preta temporária a cada transição de tela.
2. A tela de detalhe de produto (`ProductDetailFragment` / `fragment_product_detail.xml`) utilizava fundo preto fixo (`#11061F`) com texto branco sobre cards brancos.
3. As telas de criação de interferências (Pedido de Compra e Promoção em `fragment_creation.xml` / `CreationFragment.kt`) mantinham um visual arcaico com botões cinzas/roxos blocados e campos de texto sem padrão moderno.
4. A seleção de datas dependia de digitação manual de texto sem um componente visual de calendário interativo integrado à paleta do aplicativo.

O objetivo desta mudança é corrigir e modernizar esses elementos, garantindo consistência estética, alto contraste e suporte a seleção interativa de data tanto no modo claro quanto no modo escuro.

## Scope

**In scope:**
- Definição de tokens de cores semânticos para o skeleton (`skeleton_screen`, `skeleton_placeholder`, `skeleton_highlight`) em modo claro e escuro.
- Atualização do `SkeletonLoadingScreen` para utilizar cores dinâmicas via contexto/tema em vez de constantes hexadecimais estáticas.
- Atualização do layout `fragment_product_detail.xml` e drawables (`bg_flow_low.xml`) para aplicar tokens semânticos (`@color/color_petroleo`, `@color/color_text_title`, `@color/color_text_subtitle`).
- Modernização do layout `fragment_creation.xml`: inputs estilizados (`bg_input_field.xml`), cards para itens de produto com `@drawable/bg_card_summary`, e botões primários/secundários padronizados.
- Criação do vetor de ícone de calendário (`ic_calendar.xml`).
- Integração de `DatePickerDialog` estilizado com as cores do tema QuiStock (`ThemeOverlay.QuiStock.DatePicker`) nas telas de criação (Pedido de Compra e Promoção).
- Manutenção da compatibilidade com testes instrumentados existentes (`OrderAndPromotionFragmentTests`, `SkeletonLoadingScreenTests`).

**Out of scope:**
- Mudança nas regras de negócio de elegibilidade ou submissão de ações.
- Alteração nos contratos de API de interferências.

## Expected behavior

- Ao abrir qualquer tela no modo claro, o skeleton exibe uma superfície clara suave (`#F9F7FA`) com placeholders em transição de tom claro, sem flashes pretos.
- No modo escuro, o skeleton mantém superfícies escuras suavizadas confortáveis.
- A tela de Detalhes do Produto no modo claro apresenta fundo claro, textos legíveis e card de resumo contrastante.
- Na tela de Pedido de Compra e Promoção:
  - Os produtos elegíveis são exibidos como cards organizados e clicáveis, com textos nítidos.
  - Os campos de quantidade e data possuem bordas suaves, fundo adequado e tipografia Montserrat.
  - Ao lado de cada campo de data ("Data desejada", "Data inicial", "Data final") há um ícone de calendário.
  - Ao tocar no ícone de calendário ou no campo de data, abre-se um calendário modal com cabeçalho violeta e cores do tema ativo para seleção da data.
  - Ao selecionar a data, o campo é preenchido no formato esperado (`AAAA-MM-DD`).

## Acceptance criteria and evidence

| ID | Observable given / when / then | Planned test or check | Status |
| --- | --- | --- | --- |
| AC-01 | Dado o app em modo claro, quando uma tela com skeleton é aberta, então o fundo do skeleton é claro (`#F9F7FA`) com placeholders claros. | Verificação de código e testes em `SkeletonLoadingScreenTests` | Verificado |
| AC-02 | Dado o app em modo escuro, quando o skeleton é exibido, então utiliza tons escuros suavizados correspondentes. | Verificação de código e testes em `SkeletonLoadingScreenTests` | Verificado |
| AC-03 | Dado que o usuário acessa a tela de detalhes do produto no modo claro, o fundo e textos utilizam cores semânticas legíveis e o card exibe conteúdo nítido. | Verificação de layout XML e compilação | Verificado |
| AC-04 | Dado que o usuário acessa o formulário de pedido de compra ou promoção, os produtos elegíveis aparecem estilizados em formato de card. | Verificação de layout e compilação | Verificado |
| AC-05 | Dado o campo de data desejada no formulário de criação, quando o usuário clica no ícone de calendário, então um seletor de data nas cores do tema é exibido e preenche o campo no formato `AAAA-MM-DD`. | `OrderAndPromotionFragmentTests` e validação de compilação | Verificado |
| AC-06 | Todos os testes unitários e de qualidade do projeto passam (`testDebugUnitTest`, `spotlessCheck`, `detekt`, `lintDebug`, `assembleRelease`). | Comandos Gradle de verificação | Verificado |

## Implementation outcome

Todas as verificações foram executadas e aprovadas com sucesso no Gradle:
1. `spotlessApply` e `spotlessCheck`: formatação de código aprovada sem divergências.
2. `detekt`: análise estática executada com sucesso e 0 erros.
3. `testDebugUnitTest`: suíte de testes unitários executada com 100% de sucesso.
4. `lintDebug`: análise do Android Lint concluída sem erros bloqueantes.
5. `assembleRelease`: APK de release gerado com sucesso.

## Technical impact

- Arquivos afetados:
  - `app/src/main/res/values/colors.xml`
  - `app/src/main/res/values-night/colors.xml`
  - `app/src/main/res/values/themes.xml`
  - `app/src/main/res/values-night/themes.xml`
  - `app/src/main/java/com/quistock/quistock/presentation/common/SkeletonLoadingScreen.kt`
  - `app/src/main/res/layout/fragment_product_detail.xml`
  - `app/src/main/res/drawable/bg_flow_low.xml`
  - `app/src/main/res/drawable/ic_calendar.xml`
  - `app/src/main/res/drawable/bg_input_field.xml`
  - `app/src/main/res/drawable/bg_btn_secondary.xml`
  - `app/src/main/res/layout/fragment_creation.xml`
  - `app/src/main/res/layout/fragment_creation_hub.xml`
  - `app/src/main/java/com/quistock/quistock/presentation/interferences/CreationFragment.kt`
  - `app/src/main/java/com/quistock/quistock/presentation/interferences/CreationFields.kt`
  - `docs/sdd/specs/visual-redesign-creation-and-skeleton-light-mode.md`
