# Navegação inferior por cargo: repositor e gerente

**Status:** Draft — composição final e alcance da barra pendentes  
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
- `fragment_home.xml` também contém cinco controles, com rótulo Movimentações na posição equivalente a Interferências. A existência desses layouts não comprova seleção por cargo nem funcionamento dos cliques.
- A composição atual não representa simplesmente quatro itens comuns mais um adicional: Produtos não aparece na barra gerencial citada. Não adotar essa divergência como decisão de produto implícita.
- `NavGraph.kt` e `nav_graph.xml` já registram Home, Interferências, Interferências do gerente, Produtos, Funcionários, Notificações e Chatbot. A demo conecta percursos próprios; suas decisões não autorizam automaticamente rotas na aplicação normal.
- O perfil atual usa `UserRole.EMPLOYEE` e `UserRole.MANAGER`. “Repositor” no pedido corresponde ao perfil operacional `EMPLOYEE`, hoje rotulado Funcionário; esta entrega não altera o nome do cargo. Ver a atualização normativa de cargos em [profile-user-store-data.md](profile-user-store-data.md) e [context.md](../context.md).

### Variante e disponibilidade

1. Usar exclusivamente o perfil válido da sessão ativa, já carregado pelo fluxo compartilhado. `EMPLOYEE` recebe a variante de repositor e `MANAGER` recebe a variante gerencial, com exatamente um botão adicional em relação à primeira. Não deduzir cargo por texto da tela, credenciais ou dados de exemplo.
2. Enquanto sessão/perfil estiverem carregando, indisponíveis, rejeitados ou em recuperação, não liberar a barra nem cliques protegidos. Reutilizar a barreira e o retry existentes, sem nova consulta de perfil provocada pela barra.
3. Logout, invalidação e mudança de sessão removem imediatamente a variante anterior. Um resultado de perfil de outra geração não pode restaurar botões ou executar navegação.
4. Outros cargos continuam sujeitos ao bloqueio de entrada existente; não recebem uma variante padrão permissiva.

### Composição e destinos

A matriz abaixo identifica candidatos existentes, sem aprovar a composição final. Fechar O-01 e O-02 antes de implementar os cliques dependentes.

| Área candidata | Destino existente | Decisão pendente |
| --- | --- | --- |
| Início | `homeFragment` | Participação e posição na composição final |
| Interferências / Movimentações | `interferencesFragment` ou `managerInterferencesFragment` | Rótulo único e confirmação de destino por cargo |
| Produtos | `productControlFragment` | Presença em cada variante |
| Funcionários | `employeeFragment` | Confirmar se é o botão adicional exclusivo do gerente |
| Notificações | `notificationsFragment` | Aba ou acesso apenas pelo cabeçalho |
| Chat / Chatbot | `chatbotFragment` | Participação, posição e rótulo único |

### Interação, layout e retorno

- Nas telas aprovadas em O-03, manter uma única barra visível, fixa na parte inferior da área útil. A rolagem do conteúdo não desloca a barra e o último item da tela não fica encoberto. Respeitar os insets do sistema, sem somar espaçamento inferior duplicado.
- Tocar uma aba abre seu destino aprovado e atualiza o estado selecionado. A seleção acompanha o destino real, inclusive após Voltar e recriação; não fica presa ao último clique.
- Tocar a aba já selecionada ou tocar rapidamente durante uma transição não empilha cópias nem gera exceção de navegação. Não disparar operações de negócio pelo clique de navegação.
- Voltar do Android permanece coerente com a pilha de navegação. A política entre abas, preservação de estado e associação de telas filhas serão fechadas em O-04 antes da implementação correspondente.
- Destino incompatível com cargo ou sessão não pode ser aberto por listener antigo ou restauração da pilha. Ocultar um botão não substitui autorização de backend e não altera contratos remotos.
- Cada botão tem nome acessível coerente com seu destino, área de toque adequada e estado selecionado identificável por tecnologia assistiva, sem depender apenas de cor. Reutilizar os recursos visuais existentes quando compatíveis com a composição aprovada.
- O comportamento com teclado e a presença da barra em formulários/detalhes dependem de O-03; a barra não pode encobrir campos ou ações.

## Acceptance criteria and evidence

Critérios dependentes das decisões Open só estarão prontos para implementação após seu fechamento.

| ID | Observable given / when / then | Planned test or check |
| --- | --- | --- |
| AC-01 | Dado perfil válido `EMPLOYEE`, ao liberar a navegação, a barra apresenta exatamente os itens e a ordem aprovados para repositor. | Teste unitário da seleção por cargo e instrumentado da barra real. |
| AC-02 | Dado perfil válido `MANAGER`, a barra apresenta os mesmos itens comuns e exatamente um botão adicional aprovado. | Testes unitário e instrumentado comparando composição, ordem e visibilidade nas duas variantes. |
| AC-03 | Ao tocar cada aba, abre o destino aprovado para o cargo e a área atual aparece selecionada. | Testes instrumentados parametrizados por cargo, origem, controle e destino; cobrir retorno e telas filhas conforme O-04. |
| AC-04 | Ao repetir toques ou tocar a aba atual, não há destino duplicado, exceção ou operação de negócio. | Testes instrumentados com cliques reais e inspeção da pilha do NavController. |
| AC-05 | Durante carregamento, falha/recuperação de perfil, logout ou sessão inválida, nenhum clique da barra libera conteúdo protegido. | Testes da coordenação de sessão e instrumentados, incluindo listener anterior e resultado de geração antiga. |
| AC-06 | Ao trocar de uma sessão gerencial para uma operacional, não resta botão exclusivo nem destino gerencial restaurado. | Teste de regressão de troca de sessão e restauração/recriação com estados controlados. |
| AC-07 | Ao recriar a Activity e usar Voltar, seleção e pilha seguem a política aprovada, sem nova consulta de perfil pela barra. | Testes instrumentados de recriação/retorno e verificação da quantidade de consultas com double. |
| AC-08 | Ao rolar, usar navegação gestual ou três botões e abrir teclado nas telas previstas, a barra e o conteúdo continuam utilizáveis. | Check manual em dispositivo/emulador, necessário para avaliar insets, sobreposição e teclado. |
| AC-09 | Com leitor de tela e fonte ampliada, os controles têm nomes corretos, foco utilizável e seleção anunciada. | Check manual com TalkBack/fonte ampliada e teste instrumentado das descrições/estado selecionado. |

## Technical impact

Áreas prováveis: `presentation/activity/MainActivity.kt`, coordenação compartilhada de sessão/perfil, fragments e layouts que já declaram barras, `app/navigation/NavGraph.kt` e `res/navigation/nav_graph.xml`. A localização final da barra deve evitar duplicação visível e preservar os layouts da demo.

Manter Views/XML, ViewBinding, ViewModels e Koin nos padrões existentes; não adicionar camada ou biblioteca de navegação sem necessidade concreta. A UI consome o perfil do domínio e não acessa Retrofit/DTOs. Nenhuma migração, persistência nova, endpoint ou alteração de backend prevista.

Na implementação, seguir Red–Green–Refactor para seleção por cargo, cliques e regressões de sessão. Executar `testDebugUnitTest`, `spotlessCheck`, `detekt`, `lintDebug` e `assembleRelease`; executar `connectedDebugAndroidTest` com dispositivo/emulador disponível. Registrar limitações e evidências reais; o relatório unitário não comprova o gate combinado de 80% linhas e 70% branches.

## Decisions and open questions

| Item | Status | Source or decision owner |
| --- | --- | --- |
| Barra inferior com variantes por cargo; gerente possui um botão adicional | Decidido | Solicitação de 25/10/2026 |
| Cargo derivado do perfil autenticado e isolamento entre sessões | Definido nesta spec em continuidade à barreira existente | Specs de perfil e logout |
| O-01 — Quais são os itens comuns, sua ordem e seus rótulos? | Open — layouts atuais divergem | Solicitante / Produto |
| O-02 — O botão adicional do gerente é Funcionários? Quais os destinos finais por cargo, incluindo Interferências? | Open — há indícios nos layouts, sem confirmação de produto | Solicitante / Produto |
| O-03 — Em quais telas a barra permanece visível, incluindo perfil, notificações, detalhes, formulários e teclado aberto? | Open | Solicitante / Design |
| O-04 — Qual a política de Voltar entre abas, preservação/reseleção e destaque em telas filhas? | Open | Solicitante / Produto |

Não transformar os candidatos em requisitos aprovados. Fechar as decisões acima e atualizar a matriz de navegação e os testes planejados antes de implementar o comportamento dependente.

## Implementation outcome

Somente especificação criada. Nenhum código funcional alterado e nenhum teste, build ou check Gradle executado nesta etapa documental. Critérios e verificações acima são planejados, não evidência de execução.
