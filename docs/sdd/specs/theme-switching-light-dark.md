# [QUIS-VISUAL] Suporte a Tema Claro, Redução de Contraste no Escuro e Alternador de Tema

**Status:** Ready for implementation  
**Issue:** feat/visual-changes  
**Product decision owner:** Isabella Camargo / QuiStock Team

## Problem and goal

O aplicativo QuiStock operava exclusivamente com um tema escuro de alto contraste (fundo preto-petróleo `#11061F` com texto quase branco `#F8E9F9`), causando cansaço visual e sem alternativa para ambientes iluminados.
O objetivo desta mudança é:
1. Introduzir o suporte oficial ao **modo claro** com superfícies limpas e legíveis mantendo a identidade visual violeta (`#7F3AEF`).
2. Suavizar o **modo escuro** com tons menos contrastantes e confortáveis para leitura prolongada.
3. Disponibilizar um botão interativo nas duas versões da aplicação para alternar rapidamente entre os temas claro e escuro, persistindo a preferência do usuário.

## Scope

**In scope:**
- Definição de tokens de cores para o modo claro em `res/values/colors.xml` e temas diurnos em `res/values/themes.xml`.
- Definição de tokens de cores suavizados (menos contrastantes) para o modo escuro em `res/values-night/colors.xml` e temas noturnos em `res/values-night/themes.xml`.
- Vetores de ícone de alternância de tema diurno e noturno (`ic_theme_toggle.xml`).
- Componente `ThemeManager` para alternar o modo noturno (`AppCompatDelegate`), persistir no SharedPreferences e inicializar no ciclo de vida do app (`QuiStockApplication`).
- Botão seletor de tema no cabeçalho da Home (`HomeFragment`), Login (`LoginFragment`) e Perfil (`ProfileFragment`).
- Atualização do `enableEdgeToEdge` em `MainActivity` com `SystemBarStyle.auto` para adaptar ícones da barra de status automaticamente ao tema.
- Testes unitários para o `ThemeManager`.
- Adaptação dinâmica de ícones vetoriais com tokens semânticos (`color_icon`, `color_icon_inactive`, `color_limao`) para garantir visibilidade e contraste em todas as telas no modo claro e no modo escuro.

**Out of scope:**
- Alterações em contratos de APIs de autenticação ou criação de interferências.
- Alteração nas regras de negócio ou de permissão de perfis.

## Expected behavior

- Sobre roxo sólido o texto é sempre branco, nos dois temas. Nas demais superfícies, usar tokens do tema em vez de cores fixas: `color_branco` é escuro no modo claro apesar do nome, e `white`/`#FFFFFF` não se adaptam.
- Ao abrir o app pela primeira vez sem preferência salva, o app mantém a identidade padrão noturna suavizada.
- Em qualquer uma das versões (clara ou escura), o usuário visualiza o botão de alternância de tema no cabeçalho.
- Ao clicar no botão em modo escuro, o aplicativo alterna instantaneamente para o modo claro.
- Ao clicar no botão em modo claro, o aplicativo retorna para o modo escuro.
- A preferência escolhida persiste após fechar e reabrir o aplicativo.

## Acceptance criteria and evidence

| ID | Observable given / when / then | Planned test or check |
| --- | --- | --- |
| AC-01 | Dado o app em modo escuro, quando o usuário clica para alternar tema, então o modo é alterado para claro e a preferência é persistida. | Teste unitário `ThemeManagerTest` |
| AC-02 | Dado o app em modo claro, quando o usuário clica para alternar tema, então o modo é alterado para escuro e a preferência é persistida. | Teste unitário `ThemeManagerTest` |
| AC-03 | Dado que nenhuma preferência de tema foi gravada, quando o app inicia, então o padrão é o tema escuro suavizado. | Teste unitário `ThemeManagerTest` |
| AC-04 | O botão de alternância de tema está presente na interface tanto em modo claro quanto em modo escuro. | Verificação de layout e build |
| AC-05 | No modo escuro, os fundos e textos utilizam tons suavizados de menor contraste em `res/values-night/colors.xml`. | Verificação de recursos XML |
| AC-06 | Todos os testes unitários do projeto passam sem regressão. | `.\gradlew.bat testDebugUnitTest` |
| AC-07 | Em qualquer layout no modo claro, nenhum texto fica com contraste menor que 3:1 contra o fundo efetivo (sem branco sobre branco). | `LightModeContrastTests` |
| AC-08 | Em qualquer layout, texto sobre fundo roxo sólido (`color_violeta`) é branco. | `LightModeContrastTests` |
| AC-09 | Textos com cor definida por código (mensagem de Funcionários, histórico do Chatbot, subtítulo de Interferências) usam tokens do tema. | Revisão de código; dependem de dados reais e não são inflados pelo teste |

## Technical impact

- Arquivos adicionados/modificados:
  - `docs/sdd/specs/theme-switching-light-dark.md`
  - `app/src/main/res/values/colors.xml`
  - `app/src/main/res/values-night/colors.xml`
  - `app/src/main/res/values/themes.xml`
  - `app/src/main/res/values-night/themes.xml`
  - `app/src/main/res/drawable/ic_theme_toggle.xml`
  - `app/src/main/res/drawable-night/ic_theme_toggle.xml`
  - `app/src/main/res/layout/fragment_home.xml`
  - `app/src/main/res/layout/fragment_login.xml`
  - `app/src/main/res/layout/fragment_profile.xml`
  - `app/src/main/res/values/strings.xml`
  - `app/src/main/java/com/quistock/quistock/presentation/common/ThemeManager.kt`
  - `app/src/main/java/com/quistock/quistock/app/QuiStockApplication.kt`
  - `app/src/main/java/com/quistock/quistock/presentation/activity/MainActivity.kt`
  - `app/src/main/java/com/quistock/quistock/presentation/home/HomeFragment.kt`
  - `app/src/main/java/com/quistock/quistock/presentation/login/LoginFragment.kt`
  - `app/src/main/java/com/quistock/quistock/presentation/profile/ProfileFragment.kt`
  - `app/src/test/java/com/quistock/quistock/presentation/common/ThemeManagerTest.kt`


## Implementation outcome

- **AC-01 & AC-02 & AC-03:** `ThemeManager` implementado e coberto com 100% de sucesso por testes unitários em `ThemeManagerTest.kt`.
- **AC-04:** Botão seletor de tema (`btnThemeToggle`) adicionado aos layouts de Home (`fragment_home.xml`), Login (`fragment_login.xml`) e Perfil (`fragment_profile.xml`), com ícone adaptável de lua/sol (`ic_theme_toggle.xml` e `drawable-night/ic_theme_toggle.xml`).
- **AC-05:** Paleta noturna suavizada com tons de menor contraste (`#1E182A` fundo, `#282038` superfícies e `#EDE3F0` textos) configurada em `res/values-night/colors.xml`. Paleta clara definida em `res/values/colors.xml` e `res/values/themes.xml`.
- **AC-06:** Todas as verificações de qualidade e testes executados com sucesso:
  - `testDebugUnitTest`: PASSOU (todas as suítes de testes unitários).
  - `spotlessCheck`: PASSOU.
  - `detekt`: PASSOU.
  - `lintDebug`: PASSOU.
  - `assembleRelease`: PASSOU (APK de release gerado com sucesso).
- **Ícones no Modo Claro:**
  - Tokens semânticos adicionados: `color_icon` (`#1A1126` claro, `#EDE3F0` escuro), `color_icon_inactive` (`#6C6274` claro, `#AFA0B6` escuro), `color_icon_accent` (`#7F3AEF` claro, `#AF36DF` escuro).
  - Ícones da barra de navegação inferior (`ic_home`, `ic_interferences`, `ic_product_control`, `ic_employee`, `ic_chatbot`) e suas versões ativas (`*_active`) adaptados para utilizar `@color/color_icon_inactive` e `@color/color_limao`.
  - Ícones gerais de interface (`ic_notification`, `ic_menu`, `ic_profile`, `ic_new_chat`, `arrow_right`, `arrow_down`, `arrow_up`, `ic_carrinho`, `ic_transport`, `ic_wing`, `title_login`, `bem_vindo`, `logotipo`) atualizados com `@color/color_icon`, garantindo leitura e contraste em fundos claros.
- **Revisão de contraste do modo claro (04/11/2026):** auditoria dos layouts encontrou 81 problemas em 15 layouts, incluindo quatro telas com fundo fixo `#11061F` (pedido, promoção e detalhes do gerente). Cores fixas viraram tokens que mantêm no escuro o valor anterior. No escuro, mudam apenas sete itens sobre roxo, que passam a branco (AC-08). No claro, `color_limao` e `color_amarelo_tag` foram escurecidos para legibilidade, e o popup de sucesso ganhou verde mais escuro. Fora do layout inflado, estados dinâmicos (badges, bolhas e histórico do Chatbot) não têm verificação visual no app real.
- **Logo e big number (04/11/2026):** no modo claro, `logotipo` usa `color_logo` (roxo; no escuro permanece `#F8E9F9`) e o ponto de "Vencimento curto" usa `color_dot_expiry` (`#F3F02E` nos dois temas). Textos e badges amarelos mantêm `color_amarelo_tag` escuro, pois o amarelo vivo é ilegível sobre branco. Verificação: conferência visual de splash e Home renderizadas.
