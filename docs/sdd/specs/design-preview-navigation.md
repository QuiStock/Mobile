# Navegação do protótipo para avaliação de design

**Status:** Complete  
**Issue:** none  
**Product decision owner:** Open — equipe de design / solicitante

## Problem and goal

Permitir que a equipe de design percorra as páginas existentes pelos controles do aplicativo, na sequência de cada tarefa, antes da implementação funcional. Fonte: solicitação do usuário em 15/10/2026 e autorização de implementação na mesma conversa.

O grafo atual registra 28 destinos e diversas ações. A Home apenas infla o layout; vários fragments de funcionários, gerente e confirmação também são estruturas de apresentação sem listeners. Já existem navegação FAQ/contato, abertura do detalhe de produto, validações de formulários, confirmação de pedido/promoção e login com sessão real. Uma ação declarada no grafo não comprova que o botão funciona.

## Scope

**In scope:** conectar telas existentes, botões de avanço/retorno, barras inferiores, cards com destino definido e confirmações visuais; permitir demonstração com dados fictícios e sem dependência de rede; testar os percursos e a pilha de navegação.

**Out of scope:** autenticação nova, permissões por cargo, APIs, persistência, CRUD, pesquisa, filtros, câmera, envio de contato ou mensagens reais, alterações visuais extensas e criação de páginas que ainda não existem.

## Expected behavior

### Estratégia proposta

Disponibilizar o build type demo, aprovado pelo usuário junto com um seletor visível de visão operacional/gerencial. Ele instala separadamente e reutiliza os layouts com DemoActivity e DemoPageFragment exclusivos do source set demo. A Application dessa variante não inicia Koin; os fragments reais e suas ViewModels não são instanciados. O manifest remove a permissão INTERNET e o inicializador Firebase. Eventos de sessão real não redirecionam a navegação, a entrada não exige credenciais e o chatbot não envia requisições. Debug e release preservam autenticação, restauração e expiração existentes.

Reutilizar Android Navigation, nav_graph.xml, NavGraph.kt, fragments e XML existentes. Usar ViewBinding nos handlers alterados, seguindo os padrões vizinhos. Não introduzir biblioteca de roteamento, camada de domínio, repositório ou ViewModel só para encaminhar cliques. Centralizar em app/navigation apenas coordenação compartilhada que tenha uso concreto.

### Mapa proposto de percursos

As rotas abaixo são propostas para a demonstração, não decisões sobre processos reais de negócio. Conferir os IDs e rótulos dos controles antes de conectar cada um.

| Percurso | Ação e destino esperado |
| --- | --- |
| Entrada | Splash → Onboarding → Login → Loading → Home. Splash e Loading duram 900 ms, com cancelamento pelo ciclo de vida; Login ganha um botão visível Criar conta exclusivo da demo. |
| Cadastro pessoal | Login → Cadastro pessoal → Login, usando os controles existentes; retorno não cria sessão nem conta. |
| Áreas principais | Home e barras inferiores → Home, Interferências, Produtos, Funcionários, Chatbot ou Notificações conforme o controle existente e seu significado confirmado. Cabeçalho/controle de perfil → Perfil onde houver affordance correspondente. |
| Pedido | Interferências → Pedido → confirmação existente → Pedido enviado → Interferências. Cancelar confirmação mantém o formulário. |
| Promoção | Interferências → Promoção → confirmação existente → Promoção enviada → Interferências. Cancelar confirmação mantém o formulário. |
| Produtos | Controle de produtos → card de exemplo → Detalhe do produto → voltar à lista. |
| Funcionários | Funcionários → Cadastrar funcionário → Cadastro de funcionário → retornar à lista; card de funcionário → Perfil do funcionário → Editar funcionário → retornar ao perfil. |
| Gerente | Seletor persistente Visão operacional/Visão gerencial → Home → Interferências do gerente → Analisar pedido/promoção → detalhe correspondente → confirmação de aprovação → Pedido/Promoção aprovado → Interferências do gerente. |
| Perfil e ajuda | Perfil → Editar perfil → retornar ao Perfil; Perfil → FAQ → Contato → voltar à FAQ → Perfil. |
| Notificações | Home/barra → Notificações → voltar à origem; destinos de cada Analisar dependem da mensagem e permanecem Open. |
| Chatbot | Barra/atalho confirmado → Chatbot → abrir/fechar menu existente e voltar; sem conversa real. |

Todos os 28 destinos devem constar do checklist final e ser alcançáveis durante a demonstração. Loading e Splash são estados transitórios, não abas. Não adicionar links arbitrários apenas para tornar um destino alcançável.

### Retorno, conclusão e estados sem funcionalidade

- Voltar da interface e Voltar do Android retornam ao pai/origem do fluxo. Cancelar descarta a edição visual e retorna sem salvar.
- Trocar de aba não acumula cópias de Home ou da aba ativa; tocar na aba atual não empilha outro destino. Usar popBackStack/popUpTo e launchSingleTop conforme o percurso, verificando a pilha real.
- Concluir um fluxo remove formulário/confirmação da pilha: voltar da lista após uma conclusão não reabre a etapa enviada/aprovada.
- Avançar na demonstração não depende de preencher campos reais. Dados e resultados são apenas exemplos em memória; não exibir afirmação de gravação real.
- Manter os diálogos existentes quando fizerem parte da sequência visual. Rejeitar, enviar contato e outros controles sem destino definido não devem simular um resultado de negócio inventado; seu tratamento depende das decisões Open abaixo.
- Toques repetidos durante uma transição não causam navegação duplicada ou exceção por ação inválida no destino atual. Retornar e recriar a Activity não pode redirecionar o protótipo por estado de sessão real.
- Controles novos ou alterados precisam de identificação acessível; não usar áreas invisíveis para liberar telas.

## Acceptance criteria and evidence

| ID | Observable given / when / then | Planned test or check |
| --- | --- | --- |
| AC-01 | Ao iniciar a demonstração offline, é possível chegar à Home sem credenciais e visitar os 28 destinos pelos percursos definidos. | Testes instrumentados de entrada e percursos; checklist manual dos 28 destinos, necessário para avaliar a continuidade visual. |
| AC-02 | Ao tocar cada botão/card com rota definida, abre o destino correspondente da matriz. | Testes instrumentados parametrizados por origem, controle e destino, exercitando o clique real; verificar somente o XML é insuficiente. |
| AC-03 | Voltar, cancelar, concluir e trocar abas preservam as regras de pilha descritas. | Testes instrumentados com NavController e navegação real, incluindo Voltar do Android, aba repetida e origem alternativa. |
| AC-04 | Avançar/cadastrar/salvar/aprovar no protótipo permite seguir a sequência visual sem operações reais. | Testes dos fluxos com campos vazios e confirmação/cancelamento; doubles que falham caso sejam chamadas operações de rede ou persistência de negócio. |
| AC-05 | Execução normal preserva login, sessão e validações existentes, e não oferece bypass de demonstração. | Executar e complementar testes existentes de LoginFragment, SessionNavigation e OrderAndPromotionFragment; verificar isolamento na variante release. |
| AC-06 | Toques rápidos, retorno e recriação da Activity não quebram o percurso nem duplicam destinos. | Testes instrumentados de regressão e recriação, cobrindo entrada, troca de abas e conclusão. |
| AC-07 | A equipe reconhece o caminho pelos controles existentes e distingue conteúdo demonstrativo de operação real. | Revisão manual com design, em dispositivo/emulador, incluindo foco, rótulos, teclado e rolagem; avaliação visual não é substituída pelos testes de destino. |

## Technical impact

Arquivos previstos: app/src/main/res/navigation/nav_graph.xml; app/src/main/java/com/quistock/quistock/app/navigation/NavGraph.kt; MainActivity e coordenação de sessão; fragments dos percursos e seus layouts quando faltarem IDs. Configuração de build/source sets poderá ser necessária para isolar a demonstração. Nenhuma migração de dados ou contrato de API.

### Sequência de implementação

1. Fechar decisões Open que impedem rotas; inventariar controle → destino → retorno nos 28 destinos. Atualizar esta spec e reconciliar as specs de tela afetadas.
2. Escrever primeiro os testes da entrada de demonstração e da preservação da execução normal, observar falha e implementar o isolamento mínimo.
3. Em ciclos Red–Green–Refactor, conectar entrada/Home/barras; depois Produtos, Pedido/Promoção; depois Funcionários/Gerente; por fim Perfil/Ajuda/Notificações/Chatbot. Testar cliques e retornos antes de avançar para o próximo grupo.
4. Cobrir conclusão, abas repetidas, toque duplo, recriação e ausência de efeitos reais; revisar a pilha e retirar duplicações comprovadas.
5. Executar .\gradlew.bat testDebugUnitTest spotlessCheck detekt lintDebug assembleRelease, além dos comandos equivalentes da variante de demonstração que vier a ser definida. Com dispositivo disponível, executar connectedDebugAndroidTest e testes da variante. Validar cobertura combinada no CI: 80% linhas e 70% branches; relatório unitário local não substitui esse gate.
6. Realizar passeio com design cobrindo os 28 destinos e registrar evidências/limitações antes de considerar concluído.

## Decisions and open questions

| Item | Status | Source or decision owner |
| --- | --- | --- |
| Forma de distribuir/selecionar demonstração isolada | Decidido: build type demo com applicationId separado | Aprovação explícita do usuário |
| Acesso às duas visões | Decidido: seletor persistente que retorna à Home | Aprovação explícita do usuário; não representa controle real por cargo |
| btnFolha e acesso ao Perfil | Resolvido pela inspeção | contentDescription existente identifica Chatbot e Perfil; não foi inferido apenas do ID |
| Gatilho/duração de Splash e Loading | Decisão técnica de protótipo: 900 ms | Evitar bloqueio e manter estados visíveis; ajuste visual posterior permitido |
| Editar funcionário | Demo abre EditEmployeeFragment | Plano autorizado; corrige a ambiguidade apenas no protótipo, sem mudar o contrato da versão normal |
| Retornos de cadastro e edição | Retornos da matriz autorizada, sem persistência | Solicitação de implementação do plano |
| Rejeição gerencial, Analisar das notificações, Conta/Loja e envio de contato | Open para funcionalidade futura; demo mostra aviso de indisponibilidade | Sem destino ou resultado inventado |
| Saída do protótipo | Perfil → Login, limpando pilha, sem logout real | Decisão de navegação da demo |
| Sinalização de conteúdo demonstrativo | Banner persistente e nome QuiStock Demo | Isolamento aprovado pelo usuário |

As decisões Open acima se referem à funcionalidade futura; na demo, esses controles mostram um aviso de indisponibilidade.

## Implementation outcome

Implementada a variante demo com 28 destinos, seletor de visão, navegação pelos controles existentes, confirmações e retornos sem operações reais. Os layouts são compartilhados; a coordenação da demo fica isolada em app/src/demo. A entrada não instancia a sessão real e o manifest não permite acesso à internet.

DemoNavigationTests cobre entrada, cadastro pessoal, percursos operacionais, funcionários, perfil/ajuda, aprovações gerenciais, cancelamento, recriação e repetição da aba Home. A verificação estática confirmou os 28 destinos e suas referências de layout; assembleDemo e spotlessApply concluíram com sucesso. Na execução anterior à interrupção, spotlessCheck, detekt e lintDebug concluíram, e testDebugUnitTest ficou UP-TO-DATE. A inspeção do APK confirmou o applicationId separado e a ausência da permissão INTERNET.

O usuário informou que a validação passou e autorizou os commits e o push. Nenhuma validação foi repetida nesta etapa. O relato não especifica os comandos executados nem comprova percentuais de cobertura combinada; a execução instrumentada pelo agente não foi concluída devido às limitações do emulador.
