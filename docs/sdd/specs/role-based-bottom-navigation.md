# Navegação inferior por cargo: repositor e gerente

**Status:** Ready for implementation — decisões fechadas em 28/10/2026
**Issue:** none  
**Product decision owner:** solicitante

## Problem and goal

Habilitar a barra de navegação no fim da tela para o usuário autenticado, com uma variação para repositor e outra para gerente, que possui um botão adicional. Fonte: solicitação de 25/10/2026.

A barra deve permitir navegar pelas áreas principais e indicar a área atual, respeitando o cargo do perfil da sessão ativa. Esta spec trata da aplicação normal; a demonstração tem navegação própria descrita em [design-preview-navigation.md](design-preview-navigation.md).

## Scope

**In scope:** seleção da variante por cargo, posicionamento inferior, cliques e destinos das abas, estado selecionado, retorno e prevenção de navegação duplicada, acessibilidade e integração com a barreira de sessão existente.

**Out of scope:** implementar operações das telas de destino, CRUD de funcionários, aprovação/envio de interferências, contratos ou permissões de backend, novos cargos, seletor manual de cargo, alterações na demo e reformulação visual geral.

## Expected behavior

### Fontes e estado observado

- `fragment_interferences.xml` contém quatro controles: Início, Interferências, Produtos e Chat.
- `fragment_manager_interferences.xml` contém cinco: Início, Interferências, Funcionários, Notificações e Chatbot.
- Na branch `feat/functional-navigation`, o commit `623cd81` substituiu Notificações por Controle de produtos na Home e conectou esse controle na demo. A ordem da Home é Início, Movimentações, Funcionários, Controle de produtos e Chatbot; ainda diverge da ordem respondida pelo solicitante. A existência desses layouts não comprova seleção por cargo nem funcionamento dos cliques na aplicação normal.
- A barra de Interferências gerencial ainda contém Notificações e não contém Produtos. As barras inspecionadas usam ImageButtons sem texto visível; contentDescription não atende sozinho à escolha de ícones com texto. Essas diferenças são adequações de implementação, não decisões de produto em aberto.
- `NavGraph.kt` e `nav_graph.xml` já registram Home, Interferências, Interferências do gerente, Produtos, Funcionários, Notificações e Chatbot. A demo conecta percursos próprios; suas decisões não autorizam automaticamente rotas na aplicação normal.
- O perfil atual usa `UserRole.EMPLOYEE` e `UserRole.MANAGER`. “Repositor” no pedido corresponde ao perfil operacional `EMPLOYEE`, hoje rotulado Funcionário; esta entrega não altera o nome do cargo. Ver a atualização normativa de cargos em [profile-user-store-data.md](profile-user-store-data.md) e [context.md](../context.md).

### Variante e disponibilidade

1. Usar exclusivamente o perfil válido da sessão ativa, já carregado pelo fluxo compartilhado. `EMPLOYEE` recebe a variante de repositor e `MANAGER` recebe a variante gerencial, com exatamente um botão adicional em relação à primeira. Não deduzir cargo por texto da tela, credenciais ou dados de exemplo.
2. Enquanto sessão/perfil estiverem carregando, indisponíveis, rejeitados ou em recuperação, não liberar a barra nem cliques protegidos. Reutilizar a barreira e o retry existentes, sem nova consulta de perfil provocada pela barra.
3. Logout, invalidação e mudança de sessão removem imediatamente a variante anterior. Um resultado de perfil de outra geração não pode restaurar botões ou executar navegação.
4. Outros cargos continuam sujeitos ao bloqueio de entrada existente; não recebem uma variante padrão permissiva.

### Composição e destinos

Composição confirmada nas respostas parciais recebidas em 28/10/2026, da esquerda para a direita:

- Repositor: **Início → Interferências → Controle de produtos → Chatbot**.
- Gerente: **Início → Interferências → Controle de produtos → Funcionários → Chatbot**.
- Usar **ícones com texto**. Funcionários é o único botão adicional, exclusivo do gerente. Notificações fica fora das duas barras; o sino do cabeçalho é acesso suficiente.

Destinos confirmados nas respostas interativas de 28/10/2026, com reforço de que Funcionários aparece somente para gerentes:

| Botão confirmado | Destino aprovado existente | Situação |
| --- | --- | --- |
| Início | `homeFragment` para ambos | Confirmado |
| Interferências | `interferencesFragment` para repositor; `managerInterferencesFragment` para gerente | Confirmado |
| Controle de produtos | `productControlFragment` para ambos | Confirmado |
| Funcionários | `employeeFragment` apenas para gerente | Confirmado; exclusivo de gerente |
| Chatbot | `chatbotFragment` para ambos | Confirmado |

### Visibilidade confirmada

| Tela ou estado | Barra |
| --- | --- |
| Home, Interferências operacional/gerencial, Controle de produtos, Lista de funcionários e Chatbot | Mostrar, respeitando o cargo |
| Notificações, Perfil, Editar perfil, FAQ e Contato | Ocultar |
| Detalhes de produto; detalhes e edição de funcionário | Ocultar |
| Formulários de pedido/promoção; confirmações de envio/aprovação | Ocultar |
| Análise gerencial de pedido/promoção | Ocultar |
| Cadastro de funcionário | Ocultar |
| Teclado aberto em tela que possui barra | Ocultar e reaparecer ao fechar o teclado |

A confirmação para descartar alterações (pergunta 9) é condicional: se houver barra em um formulário, trocar de aba exige confirmação antes do descarte. Ela não autoriza mostrar a barra nos formulários já marcados como Ocultar, nem cria confirmação global para qualquer saída de formulário.

### Estado das abas e associação de telas

- Ao retornar a outra aba, abrir sua tela inicial e preservar os filtros e a posição de rolagem existentes. Preservar também ao recriar a tela, incluindo rotação. Após encerramento do processo, reiniciar esses estados; não criar persistência em disco para essa finalidade. Não restaurar automaticamente uma tela de detalhe. Telas sem filtros ou rolagem não precisam criar esses recursos.
- Reselecionar a aba atual apenas rola para o topo; não recria destino nem reinicia filtros. Para conteúdo sem rolagem, não há deslocamento a executar. No Chatbot, a exceção aprovada é ir à mensagem mais recente ao reselecionar; ao retornar de outra aba, recuperar a posição anterior. Manter a conversa durante a sessão, sem apagá-la pela troca ou reseleção da aba.
- Associações confirmadas na pergunta 14: detalhe de produto → Controle de produtos; pedido/promoção/análise gerencial → Interferências; detalhe/cadastro/edição de funcionário → Funcionários; Perfil/Editar perfil/FAQ/Contato e Notificações → nenhuma aba selecionada.
- Essas associações não tornam a barra visível em telas marcadas como Ocultar. Só determinam seleção se a barra estiver presente conforme a matriz aprovada.

### Interação, layout e retorno

- Nas telas aprovadas em O-03, manter uma única barra visível, fixa na parte inferior da área útil. A rolagem do conteúdo não desloca a barra e o último item da tela não fica encoberto. Respeitar os insets do sistema, sem somar espaçamento inferior duplicado.
- Tocar uma aba abre seu destino aprovado e atualiza o estado selecionado. A seleção acompanha o destino real, inclusive após Voltar e recriação; não fica presa ao último clique.
- Tocar a aba já selecionada ou tocar rapidamente durante uma transição não empilha cópias nem gera exceção de navegação. Não disparar operações de negócio pelo clique de navegação.
- Em detalhes e formulários, Voltar retorna à tela anterior do fluxo. Na raiz de uma aba diferente de Início, Voltar retorna à aba anteriormente visitada, percorrendo o histórico de trocas de abas. O retorno consome esse histórico, sem registrar a própria ação como nova troca e sem criar alternância infinita entre duas abas. Reselecionar a aba atual não acrescenta entrada ao histórico. A regra específica de confirmação ao usar Voltar na Home permanece válida.
- Na Home, Voltar do Android pede confirmação antes de sair: cancelar mantém a Home; confirmar sai da tela do aplicativo sem executar logout. Reutilizar os padrões de diálogo existentes, com ações inequívocas de sair e cancelar.
- Destino incompatível com cargo ou sessão não pode ser aberto por listener antigo ou restauração da pilha. Ocultar um botão não substitui autorização de backend e não altera contratos remotos.
- Cada botão tem nome acessível coerente com seu destino, área de toque adequada e estado selecionado identificável por tecnologia assistiva, sem depender apenas de cor. Reutilizar os recursos visuais existentes quando compatíveis com a composição aprovada.
- Aplicar a matriz de visibilidade acima e ocultar a barra com teclado aberto; ela não pode encobrir campos ou ações.

## Acceptance criteria and evidence

As decisões de produto desta spec estão fechadas; as verificações abaixo são planejadas para a implementação.

| ID | Observable given / when / then | Planned test or check |
| --- | --- | --- |
| AC-01 | Dado perfil válido `EMPLOYEE`, ao liberar a navegação, a barra apresenta exatamente os itens e a ordem aprovados para repositor. | Teste unitário da seleção por cargo e instrumentado da barra real. |
| AC-02 | Dado perfil válido `MANAGER`, a barra apresenta os mesmos itens comuns e exatamente um botão adicional aprovado. | Testes unitário e instrumentado comparando composição, ordem e visibilidade nas duas variantes. |
| AC-03 | Ao tocar cada aba, abre o destino aprovado para o cargo e a área atual aparece selecionada. | Testes instrumentados parametrizados por cargo, origem, controle e destino; cobrir associações confirmadas quando a barra estiver visível. |
| AC-04 | Ao repetir toques ou tocar a aba atual, não há destino duplicado, exceção ou operação de negócio. | Testes instrumentados com cliques reais e inspeção da pilha do NavController. |
| AC-05 | Durante carregamento, falha/recuperação de perfil, logout ou sessão inválida, nenhum clique da barra libera conteúdo protegido. | Testes da coordenação de sessão e instrumentados, incluindo listener anterior e resultado de geração antiga. |
| AC-06 | Ao trocar de uma sessão gerencial para uma operacional, não resta botão exclusivo nem destino gerencial restaurado. | Teste de regressão de troca de sessão e restauração/recriação com estados controlados. |
| AC-07 | Ao recriar a Activity e usar Voltar, seleção e pilha seguem a política aprovada, sem nova consulta de perfil pela barra. | Testes instrumentados de recriação/retorno e verificação da quantidade de consultas com double. |
| AC-08 | Ao rolar, usar navegação gestual ou três botões e abrir teclado nas telas previstas, a barra e o conteúdo continuam utilizáveis. | Check manual em dispositivo/emulador, necessário para avaliar insets, sobreposição e teclado. |
| AC-09 | Com leitor de tela e fonte ampliada, os controles têm nomes corretos, foco utilizável e seleção anunciada. | Check manual com TalkBack/fonte ampliada e teste instrumentado das descrições/estado selecionado. |
| AC-10 | A barra mostra ícones com os textos aprovados, na ordem definida; Notificações não é aba em nenhum cargo. | Teste instrumentado de texto/ordem e check visual para legibilidade em telefone e tablet. |
| AC-11 | Nas telas definidas, a barra respeita a matriz de visibilidade e desaparece com o teclado, reaparecendo ao fechá-lo. | Teste instrumentado por destino e de abertura/fechamento do IME; complementar com check visual. |
| AC-12 | Reselecionar a aba rola para o topo sem reiniciar filtros; no Chatbot vai à mensagem mais recente. Trocar de aba retorna à raiz, preservando filtros/rolagem e, no Chatbot, a posição anterior da conversa. | Testes instrumentados com conteúdo rolado/filtros e conversa controlada; cobrir também recriação e reinício dos estados após encerramento do processo. |
| AC-13 | Na Home, Voltar abre confirmação; cancelar mantém a tela e confirmar sai sem logout. | Teste instrumentado das duas ações e verificação de que o fluxo de logout não foi chamado. |
| AC-14 | Após visitar Início → Controle de produtos → Chatbot, Voltar na raiz retorna a Controle de produtos e depois a Início; reseleção não adiciona histórico e o retorno não provoca alternância infinita. | Teste instrumentado de sequência entre abas, com reseleção, recriação e preservação de filtros/rolagem. |

## Technical impact

Áreas prováveis: `presentation/activity/MainActivity.kt`, coordenação compartilhada de sessão/perfil, fragments e layouts que já declaram barras, `app/navigation/NavGraph.kt` e `res/navigation/nav_graph.xml`. A localização final da barra deve evitar duplicação visível e preservar os layouts da demo.

Manter Views/XML, ViewBinding, ViewModels e Koin nos padrões existentes; não adicionar camada ou biblioteca de navegação sem necessidade concreta. A UI consome o perfil do domínio e não acessa Retrofit/DTOs. Nenhuma migração, persistência nova, endpoint ou alteração de backend prevista.

Na implementação, seguir Red–Green–Refactor para seleção por cargo, cliques e regressões de sessão. Executar `testDebugUnitTest`, `spotlessCheck`, `detekt`, `lintDebug` e `assembleRelease`; executar `connectedDebugAndroidTest` com dispositivo/emulador disponível. Registrar limitações e evidências reais; o relatório unitário não comprova o gate combinado de 80% linhas e 70% branches.

## Decisions and open questions

| Item | Status | Source or decision owner |
| --- | --- | --- |
| Barra inferior com variantes por cargo; gerente possui um botão adicional | Decidido | Solicitação de 25/10/2026 |
| Cargo derivado do perfil autenticado e isolamento entre sessões | Definido nesta spec em continuidade à barreira existente | Specs de perfil e logout |
| O-01 — Composição, ordem, rótulos e apresentação | Fechado: listas acima; ícones com texto | Respostas 1–3 do solicitante, 28/10/2026 |
| O-02 — Botão exclusivo e Notificações | Fechado: Funcionários exclusivo do gerente; sino suficiente para Notificações | Respostas 4 e 6 |
| O-02a — Destinos da matriz | Fechado: destinos aprovados; Funcionários somente para gerentes | Resposta interativa de 28/10/2026 |
| O-03 — Visibilidade nas telas respondidas, teclado e descarte condicional | Fechado conforme matriz; ocultar com teclado e confirmar descarte se houver barra em formulário | Respostas 7–9 |
| O-03a — Análise gerencial de pedido/promoção | Fechado: ocultar | Resposta interativa de 28/10/2026 |
| O-03b — Cadastro de funcionário | Fechado: ocultar | Resposta interativa de 28/10/2026 |
| O-04 — Retorno à raiz, reseleção e associação de telas filhas | Fechado para troca/reseleção: raiz com preservação em memória, exceção do Chatbot e associações acima | Respostas 10, 11 e 14 |
| O-04a — Voltar na raiz de outra aba | Fechado: retornar à aba anteriormente visitada | Decisão explícita do solicitante em 28/10/2026 |
| O-04b — Voltar na Home | Fechado: pedir confirmação antes de sair | Resposta interativa de 28/10/2026 |
| O-04c — Preservação de filtros e rolagem | Fechado: preservar entre abas e na recriação; reiniciar após encerramento do processo | Resposta interativa de 28/10/2026 |
| O-04d — Chatbot | Fechado: reselecionar vai à mensagem mais recente; retornar recupera posição anterior; manter conversa durante a sessão | Resposta interativa de 28/10/2026 |

As respostas explícitas prevalecem sobre os layouts e a demo. As decisões foram fechadas pelas respostas parciais, interativas e pela confirmação final de O-04a; não reabrir itens já respondidos por causa de divergências de código.

## Implementation outcome

Spec revisada em 28/10/2026 com as respostas parciais e inspeção estática da branch `feat/functional-navigation`, incluindo o commit `623cd81`. Esse commit altera a Home compartilhada e a rota da demo; não comprova navegação funcional por cargo na aplicação normal. `HomeFragment` inspecionado não conecta os botões inferiores, e a barra gerencial de Interferências mantém a composição anterior.

Nenhum código funcional alterado e nenhum teste, build ou check Gradle executado nesta revisão documental. Critérios e verificações acima são planejados, não evidência de execução. A árvore de trabalho estava limpa antes desta revisão.

Respostas interativas e confirmação final de O-04a incorporadas em 28/10/2026. Nenhum ponto de produto permanece Open. Nenhuma implementação funcional executada nesta atualização; verificação documental com `git diff --check`, sem testes ou build.
