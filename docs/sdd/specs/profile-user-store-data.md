# Dados do usuário e da loja no perfil e nos headers

**Status:** In progress — implementação entregue; execução de testes e validação delegadas ao solicitante  
**Issue:** none  
**Product decision owner:** solicitante

## Problema e objetivo

O perfil e os headers exibem nomes fixos ou placeholders. Exibir os dados do usuário autenticado e de sua loja de maneira consistente, consultando a API Core a cada abertura do app e compartilhando o resultado em memória. Fonte: solicitação de 20/10/2026.

Spec aprovada pelo solicitante em 20/10/2026, com autorização para implementar e usar commits semânticos em inglês. O solicitante determinou que não sejam executados testes ou validações ao final; fará a verificação manualmente.

## Escopo

**Incluído:** leitura do perfil autenticado no Core; estado compartilhado em memória; preenchimento do perfil e dos headers existentes; carregamento, ausência de vínculo, falhas e isolamento entre sessões.

**Fora do escopo:** salvar edições, enviar Contato, upload/câmera, alterar o fluxo de logout voluntário já implementado, CRUD de funcionários, selecionar loja, alterar backend e persistir perfil em disco. O bloqueio de entrada por ausência de loja e descarte da sessão em 403 pertencem a esta entrega. A demo mantém seus exemplos e sua ausência de acesso à rede.

Complementa `profile-screen.md`, que descreve o layout estático. Não substitui a spec de detalhes de outro funcionário (`employee-profile.md`): o header representa o usuário autenticado; os dados centrais do funcionário consultado são outro contexto.

## Fontes e fatos verificados

Documentação local consultada em `../DS_Backend/README.md` e `../DS_Backend/api-contract.md` (versão declarada 1.5.0, 07/10/2026), seções Authentication, 0.1 e 5.1. Conferência adicional: `ProfileController.java`, `UserDTO.java` e `UserProfileService.java` no DS_Backend.

- `GET /profile`, sem corpo e sem ID informado pelo cliente, lê a conta identificada pelo `sub` do JWT.
- HTTP 200 retorna `id`, `name`, `email`, `role`, `status`, `profile_photo_url`, `store_id`, `store_code`, `store_name`, `region_id`, `region_code`, `region_name`. `id` é string; `store_id` e `region_id` são números inteiros (`Long`) no DTO, embora o exemplo documental mostre esses vínculos nulos. Foto e vínculos podem ser nulos.
- Cargos documentados localmente: `ADMIN`, `GERENTE`, `GERENTE_REGIONAL`, `FUNCIONARIO`. A resposta do ambiente fornecida pelo solicitante em 20/10/2026 usa também `EMPLOYEE`; esse valor é convertido em `FUNCIONARIO` na fronteira data/Core. Funcionário tem uma loja ativa; gerente regional tem região. Não se pode presumir uma loja para todo usuário.
- Token ausente/inválido/expirado: 401. Conta ausente/inativa: 403. O contrato não define aqui um formato de corpo de erro para o cliente interpretar.
- A resposta já contém o nome da loja. Não é necessário listar `/branches` para resolver esse nome. Não há logo de loja nem endereço em `/profile`; não inventar esses dados ou usar produtos para deduzir o vínculo.
- O cliente Core existente usa Bearer, remove Cookie e coordena renovação/repetição limitada de 401 via `CoreSessionInterceptor` e `SessionUseCase`. Não criar um fluxo paralelo de autenticação para o perfil.
- `CORE_BASE_URL` é configurada no build. Prefixos públicos dependem do gateway; compor o caminho de perfil respeitando a base configurada. Não copiar URLs privadas para a spec.

### Mapeamento de dados

| Destino | Origem | Observação |
| --- | --- | --- |
| Nome nos headers e no centro do perfil | `name` | Mesmo usuário e mesmo resultado compartilhado |
| Loja nos headers e na linha de loja do perfil | `store_name` | Sem vínculo: bloquear entrada e mostrar “Sem loja vinculada” fora da Home; ver O-09 |
| Cargo no perfil | `role` | Traduzir cargo conhecido; cargo desconhecido invalida o perfil. FUNCIONARIO aparece como “Repositor” e GERENTE como “Gerente” |
| E-mail | `email` | Preencher Editar perfil e Contato; não adicionar campo ao perfil principal |
| Avatar do usuário | `profile_photo_url` | Carregar se fornecida; se ausente, avatar padrão. Não confundir com logo da loja; falha da imagem em O-11 |
| Identificação e vínculo em memória | `id`, `store_id`, `store_code`, campos de região | Não expor como credencial ou presumir seleção de loja |

### Inventário de headers

Layouts encontrados em `app/src/main/res/layout`, todos com prefixo `fragment_` e extensão `.xml`:

- `home`, `profile`, `edit_profile`, `notifications`, `faq`, `contact`.
- `chatbot`, `interferences`, `product_control`, `product_detail`, `order`, `promotion`.
- `employee`, `employee_profile`, `employee_detail`, `edit_employee`.
- `manager_interferences`, `manager_order_detail`, `manager_promotion_detail`.

Os 19 headers estão incluídos. Também preencher nome/e-mail em Editar perfil e Contato, sem implementar salvamento/envio. Não substituir dados centrais de outros funcionários pelo perfil autenticado. O prefill não deve sobrescrever texto que o usuário já esteja editando ao ocorrer nova renderização do mesmo estado. Acessibilidade que hoje usa nomes de exemplo também deve acompanhar os dados reais ou rótulos neutros aprovados.

## Comportamento esperado

### Requisitos confirmados

1. A API Core é a fonte dos dados; não extrair nome/loja de JWT, credenciais de login ou textos fixos.
2. Manter os dados do usuário em memória para compartilhamento entre perfil e headers. Não introduzir armazenamento persistente de perfil, inclusive SavedStateHandle, preferências ou cache HTTP em disco contendo a resposta.
3. Consultar o Core na inicialização do app, antes de liberar a Home. A restauração do processo parte sem perfil em memória. Voltar do background, rotacionar ou navegar internamente não dispara nova consulta.
4. No login, manter a tela de carregamento até autenticação e carregamento do perfil concluírem com sucesso. Somente então redirecionar à Home; falha ao carregar o perfil é falha do fluxo de login e não libera a Home.
5. Na abertura com restauração de sessão, carregar o perfil antes de redirecionar da splash. Falha no perfil não permite avançar à Home; aplicar a recuperação de O-06.
6. Nenhuma implementação deve começar enquanto existir decisão Open nesta spec.

### Fluxo esperado

- Sem sessão válida, não requisitar perfil protegido. Após autenticar ou restaurar a sessão, consultar o perfil sem exigir visita à tela Perfil. A barreira de entrada confirmada mantém o carregamento do login ou a splash até o perfil estar disponível; sucesso da autenticação isoladamente não autoriza navegar à Home.
- Compartilhar estado observável de não carregado/carregando/disponível/erro, associado à geração da sessão. Consumidores simultâneos compartilham a consulta; navegar ou rotacionar a tela não gera consultas extras por si só.
- Publicar usuário e vínculo de loja/região juntos. Perfil e headers observam o mesmo resultado e não mantêm cópias divergentes por Fragment.
- Descartar resultado tardio de sessão anterior. Limpar/inutilizar os dados ao invalidar a sessão ou trocar de conta, mesmo se houver uma consulta em andamento. Renovação de token da mesma sessão não implica nova consulta além da repetição já prevista no cliente Core.
- Não mostrar nomes de exemplo em carregamento/erro. O bloqueio de acesso à Home durante o carregamento e em falha está confirmado. Em falha de consulta, apresentar erro e “Tentar novamente” no carregamento do login ou na splash, mantendo a sessão para repetir a consulta de perfil. Usar “Não foi possível carregar seu perfil. Tente novamente.” com ação “Tentar novamente”. Não há atualização do perfil ao voltar do background nem fluxo de exibição de perfil desatualizado decorrente desse retorno.
- Tratar 401 pelo mecanismo existente. Para 403, não tentar refresh em loop nem interpretar como ausência normal de loja; descartar a sessão e voltar ao login com “Não foi possível acessar sua conta. Entre em contato com o responsável.”
- Falhas de rede, timeout, 5xx, 429 ou resposta incompatível são falhas de consulta, não perfis vazios. Repetição somente manual, respeitando Retry-After válido. Rejeitar perfil sem id, nome ou e-mail, com status diferente de ACTIVE, cargo desconhecido ou vínculo incoerente. Vínculo inteiramente nulo é estruturalmente aceito, mas ausência de loja bloqueia a entrada pela regra de negócio. Não registrar payload, nome, e-mail, URL da foto ou tokens em logs.
- Preservar legibilidade de nomes longos e acessibilidade. Headers usam uma linha com reticências; no perfil, permitir quebra de linha. Leitor de tela acessa o nome completo. Falha de foto não é falha de perfil: URL ausente/vazia/inválida ou erro no download usa avatar padrão e não bloqueia entrada. Aplicar aos avatares do usuário, inclusive Editar perfil; não aguardar download da foto para liberar a Home.

- Sem loja vinculada, não liberar a Home nem conteúdo protegido. Mostrar “Sem loja vinculada” no estado de bloqueio e orientar contato com o superior: Gerente para Repositor; Gerente Regional para Gerente. Descartar sessão e retornar ao login com “Sem loja vinculada. Entre em contato com seu gerente.” para Repositor ou “Sem loja vinculada. Entre em contato com seu gerente regional.” para Gerente. Não substituir loja por região/código ou escolher uma arbitrariamente.
- Conforme informado pelo solicitante, GERENTE_REGIONAL não acessa o app: DS_Auth retorna 403 para login com platform=mobile. Somente FUNCIONARIO e GERENTE com loja vinculada podem entrar. Bloquear também ADMIN e GERENTE_REGIONAL encontrados no perfil após restauração, descartar a sessão e voltar ao login com “Seu perfil não tem acesso ao aplicativo móvel.”; não depender apenas da recusa de login da Auth. Validar a integração na entrega futura.
- Traduzir cargos conhecidos, sem fallback “Usuário” para valores desconhecidos.

## Critérios de aceitação e evidências planejadas

Os critérios abaixo refletem as decisões aprovadas nas caixas de diálogo. Há testes automatizados adicionados para domínio, repositório e recuperação. Nenhum foi executado nesta entrega, por instrução do solicitante. Verificações instrumentadas e manuais permanecem pendentes.

| ID | Dado / quando / então observável | Teste ou verificação planejada |
| --- | --- | --- |
| AC-01 | Dado login com autenticação bem-sucedida, enquanto `GET /profile` estiver pendente, então permanecer na tela de carregamento; somente com perfil válido e elegível (FUNCIONARIO ou GERENTE, com loja vinculada) publicar os dados e navegar à Home. Sem sessão válida, não consultar. | Unitário do coordenador de login/perfil, integração local do repositório e instrumentado da barreira de navegação |
| AC-02 | Dado app inicializado com sessão restaurável, quando restaurar a sessão, então obter o perfil novamente sem recuperá-lo do disco e permanecer na splash até o perfil estar válido e elegível, antes de redirecionar à Home. | Unitário com nova instância e instrumentado de inicialização/recriação de processo e navegação; inspeção da ausência de persistência |
| AC-03 | Dado perfil disponível, quando abrir perfil e qualquer header inventariado, então mostrar nome/loja coerentes com o mesmo estado, sem dados de exemplo. | Unitários de mapeamento/estado; instrumentados de binding; checklist manual de todas as telas para cobertura visual |
| AC-04 | Dados consumidores concorrentes e rotação/navegação, quando pedirem o estado da mesma abertura/sessão, então compartilhar a consulta em andamento. | Unitário com concorrência controlada; instrumentado de recriação da Activity |
| AC-05 | Dada troca/invalidação de sessão com requisição pendente, quando a resposta antiga chegar, então não armazenar nem renderizar os dados antigos. | Unitário de corrida e de limpeza; instrumentado de troca de sessão |
| AC-06 | Dados vínculos nulos/vazios e cargos suportados ou desconhecidos, quando renderizar, então aplicar O-03/O-04 sem inventar loja. | Unitários de mapeamento e instrumentado dos estados sem vínculo |
| AC-07 | Dada falha no carregamento do perfil após autenticar, então considerar o fluxo de login falho e não liberar a Home; dada falha no perfil durante abertura pela splash, então não redirecionar à Home. Aplicar mensagens e recuperação de O-06 sem nomes fictícios. | Unitários do coordenador/ViewModel e instrumentados de erro/retry e ausência de navegação prematura |
| AC-08 | Dado 401, quando consultar perfil, então usar renovação existente e repetição limitada; dado 403, seguir O-06 sem loop. | Integração local com respostas HTTP controladas e unitário da coordenação com sessão |
| AC-09 | Dada resposta incompatível ou parcial, quando processar, então aplicar O-07 sem publicar dados misturados/parciais indevidos. | Unitários do mapper e integração local de desserialização |
| AC-10 | Dados nomes longos e leitor de tela, quando renderizar os destinos, então preservar leitura e descrições coerentes, com reticências nos headers, quebra no perfil e nome completo acessível. | Manual com fonte ampliada e TalkBack, justificado pela avaliação visual/assistiva; instrumentado para conteúdo acessível |
| AC-11 | Dado build demo, quando navegar, então continuar sem autenticação/rede real. | Inspeção do source set e jornada instrumentada demo existente, na implementação |
| AC-12 | Dado perfil já carregado na mesma sessão/processo, quando voltar do background, então reutilizar o estado em memória sem nova consulta de perfil ou nova barreira de carregamento do perfil. | Unitário do ciclo de vida e instrumentado de background/foreground com contagem de requisições |
| AC-13 | Dado perfil sem loja, quando concluir a consulta, então bloquear acesso à Home e mostrar “Sem loja vinculada” com orientação ao superior, conforme O-09. | Unitários de elegibilidade e instrumentados de bloqueio no login/splash |
| AC-14 | Dado nome/e-mail disponível, quando abrir Editar perfil ou Contato, então preencher os campos sem implementar salvamento/envio nem sobrescrever edição em curso. | Unitários de estado e instrumentados de prefill/re-renderização |
| AC-15 | Dada foto fornecida, quando exibir avatar do usuário, então carregar a foto; sem foto, usar avatar padrão. Falhas seguem O-11. | Integração local do carregamento de imagem e instrumentado de foto/fallback |
| AC-16 | Dado 403 no perfil, então descartar sessão e retornar ao login com a mensagem aprovada, sem liberar Home. | Unitário de invalidação e instrumentado de navegação/mensagem |

| AC-17 | Dado ADMIN ou GERENTE_REGIONAL no perfil restaurado, quando validar elegibilidade, então impedir entrada, descartar a sessão e retornar ao login com “Seu perfil não tem acesso ao aplicativo móvel.”, sem tratar cargo conhecido como cargo desconhecido. | Unitário de elegibilidade e instrumentado da barreira de navegação |

## Impacto técnico

Proposta: modelos/portas/casos de uso em `domain`, DTO/mapeamento e repositório no cliente Core em `data`, composição de estado em ViewModels e binding por Views/XML em `presentation`, configuração em Koin. Reusar padrões vizinhos; a spec não exige criar novas camadas ou módulos.

Integrar o ciclo de vida do perfil com a geração de sessão existente. Inspecionar os pontos de bootstrap/login/restauração antes de escolher a coordenação concreta. Alterações prováveis nos layouts inventariados incluem IDs estáveis para textos hoje sem ID. Não ampliar o escopo para implementar as features dessas telas.

Sem migração de dados ou escrita de negócio no backend. Ambiente de verificação futuro: Core simulado local para contrato/falhas; smoke em ambiente integrado autorizado ainda necessário, sem dados pessoais na evidência. Não houve acesso à API real nesta elaboração.

Na implementação futura, seguir Red–Green–Refactor para comportamento novo. Executar testes relevantes e checks do projeto (`testDebugUnitTest`, `spotlessCheck`, `detekt`, `lintDebug`, `assembleRelease`); instrumentados quando houver dispositivo. Cobertura unitária local não comprova o gate combinado da CI.

## Decisões e pontos em aberto

O-01 a O-13 foram fechados pelo solicitante nas caixas de diálogo. Não há pontos em aberto. A spec consolidada foi aprovada e sua implementação autorizada pelo solicitante.

| Item | Status | Decisão necessária / proposta | Responsável |
| --- | --- | --- | --- |
| O-01 — Abertura | Fechado | Consultar na inicialização do app e após autenticação no login. Não recarregar ao voltar do background; navegação interna e rotação não são nova abertura. | Solicitante, esclarecimento de 20/10/2026 |
| O-02 — Carregamento e navegação | Fechado | Perfil é pré-condição para entrar na Home. Login permanece no carregamento até o perfil carregar; falha no perfil é falha do login. Na abertura, carregar antes de redirecionar da splash. Recuperação e textos continuam em O-06; nomes longos em O-08. | Solicitante, esclarecimento de 20/10/2026 |
| O-03 — Sem loja/região | Fechado | Ausência de loja impede entrada no app. Mostrar “Sem loja vinculada” fora da Home e orientar contato com Gerente (Repositor) ou Gerente Regional (Gerente). Não usar região como substituto. Detalhes operacionais em O-09. | Solicitante |
| O-04 — Campos e apresentação | Fechado | Carregar foto se a API fornecer; se ausente, avatar padrão. Traduzir cargos conhecidos e rejeitar cargo desconhecido. Rótulo FUNCIONARIO em O-10; falha da imagem em O-11. | Solicitante |
| O-05 — Limites de telas/campos | Fechado | Incluir os 19 headers, campos existentes de nome/cargo/loja do perfil e prefill de nome/e-mail em Editar perfil e Contato, sem salvamento/envio. | Solicitante |
| O-06 — Falhas e recuperação | Fechado | Erro de consulta no login ou splash mantém sessão e bloqueia Home, com “Tentar novamente” para consultar perfil novamente. Renovação necessária de credenciais continua no mecanismo existente. Em 403 do perfil, descartar sessão e voltar ao login com “Não foi possível acessar sua conta. Entre em contato com o responsável.” Retry somente manual e respeitando Retry-After válido. Texto genérico em O-12. | Solicitante |
| O-07 — Resposta incompleta | Fechado | Rejeitar ausência de id/nome/e-mail, status diferente de ACTIVE, cargo desconhecido ou vínculo incoerente (como nome de loja sem ID). Vínculo inteiramente nulo é estruturalmente válido, porém a regra de ausência de loja bloqueia entrada. Falha de validação usa recuperação de consulta; 403 mantém tratamento específico. | Solicitante |
| O-08 — Nomes longos e acessibilidade | Fechado | Headers em uma linha com reticências; perfil permite quebra de linha. Leitor de tela acessa nome completo. | Solicitante |
| O-09 — Recuperação sem loja | Fechado | Descartar sessão e retornar ao login com “Sem loja vinculada. Entre em contato com seu gerente.” para Repositor ou “Sem loja vinculada. Entre em contato com seu gerente regional.” para Gerente. Não oferecer retry do perfil com a sessão descartada. | Solicitante |
| O-10 — Cargos no mobile | Fechado | FUNCIONARIO aparece como “Repositor”; GERENTE como “Gerente”. Somente esses dois cargos, ambos com loja vinculada, podem entrar. Bloquear ADMIN/GERENTE_REGIONAL também no perfil restaurado. A ausência de garantia de loja para GERENTE no Core é risco de integração documentado abaixo, sem relaxar a regra aprovada. | Solicitante |
| O-11 — Falha da foto | Fechado | URL ausente/vazia/inválida ou falha no download usa avatar padrão sem bloquear entrada. Aplicar aos avatares do usuário, inclusive Editar perfil. Não encaminhar Bearer/cookies do Core ao host da foto e não persistir imagem em disco. | Solicitante |
| O-12 — Mensagem de falha de consulta | Fechado | “Não foi possível carregar seu perfil. Tente novamente.” com ação “Tentar novamente” no login/splash. Para 429, aguardar Retry-After válido, sem disparo automático ao terminar a espera. | Solicitante |

| O-13 — Recuperação por cargo não permitido | Fechado | Para ADMIN/GERENTE_REGIONAL retornado pelo perfil restaurado, descartar a sessão e retornar ao login com “Seu perfil não tem acesso ao aplicativo móvel.” | Solicitante |

### Risco de integração registrado

O contrato consultado não garante loja para GERENTE. A regra aprovada permanece: gerente sem loja não entra. Na verificação integrada futura, confirmar que o backend/ambiente fornece vínculo para os gerentes que devem acessar o mobile; caso contrário, eles serão bloqueados conforme esta spec. Não há autorização para alterar o backend nesta entrega.

Para cargo conhecido não permitido (ADMIN/GERENTE_REGIONAL retornado pelo perfil), descartar a sessão e voltar ao login com “Seu perfil não tem acesso ao aplicativo móvel.” A mensagem é distinta da recusa 403 do Core e da ausência de loja.

## Resultado da implementação

Implementação autorizada após fechamento de O-01 a O-13:

- `ProfileUseCase` compartilha consultas e resultado em memória por geração de sessão; publicações são protegidas pela geração e respostas antigas são descartadas.
- `ProfileApi` usa `GET profile` relativo à base Core e o cliente Bearer existente. O repositório diferencia 403, falha transitória, resposta inválida e 429 com Retry-After.
- `SessionViewModel` coordena carregamento após login/restauração. `MainActivity` mantém a barreira de entrada até perfil válido e elegível; falha de autenticação/renovação também não navega prematuramente para Home. Retorno do background não requisita perfil novamente.
- Ausência de loja e cargos não permitidos invalidam sessão e retornam ao login com as mensagens aprovadas. Falhas recuperáveis mantêm sessão e oferecem retry manual.
- `ProfileBindingCallbacks` conecta slots explicitamente identificados nos 19 layouts ao estado da ViewModel. Preenche nome/e-mail de Editar perfil e Contato sem sobrescrever edição ao renderizar novamente o mesmo estado. Slots não participam do salvamento da hierarquia de Views; após recriação, o prefill parte do perfil em memória ou de nova consulta após restauração de processo.
- Foto usa cliente independente sem credenciais/cookies/redirects/cache em disco, cache apenas em memória, HTTPS e limite de 2 MiB. Imagens inválidas ou indisponíveis usam avatar padrão. Download não bloqueia entrada.
- Demo não registra os callbacks e mantém exemplos offline. Não houve alteração no backend.

Testes adicionados: `ProfileUseCaseTests`, `RetrofitProfileRepositoryTests` e `ProfileRecoveryTests`. Testes existentes de recuperação/navegação e resolução de DI foram adaptados às novas dependências e à barreira de entrada. Evidências automatizadas de concorrência, cache por sessão, rejeições, resposta tardia, retry manual e conversão de erros estão escritas, mas **não executadas**. Red–Green não foi executado por instrução explícita do solicitante.

Não foram executados testes, builds, lint, formatadores/checks, validações de cobertura, simulações, chamadas reais de API ou verificação visual/instrumentada. Essas verificações ficam para o solicitante. O vínculo de loja de gerentes no ambiente real continua sendo o risco de integração registrado acima.



### Correção dos apontamentos do Detekt

Refatoração mecânica baseada nos 18 apontamentos de `app/build/reports/detekt/detekt.md`, gerado em 20/10/2026: separar validação do perfil, leitura limitada de imagem, binding de campos/fotos, renderização/navegação da barreira e recuperação de sessão; reduzir retornos antecipados; nomear códigos HTTP e conversão de tempo; quebrar linhas longas nos testes. Sem alteração de comportamento ou do contrato aprovado, portanto não exige nova spec. Não foram adicionadas supressões, alteradas regras ou exclusões de qualidade. Os relatórios debug/release disponíveis são históricos de 05/08/2026 e não representam esta implementação.

Detekt, testes e demais validações não foram executados nesta correção, conforme instrução do solicitante; é necessário regenerar o relatório para confirmar o resultado.

### Correção do teste de recuperação de sessão

O relatório fornecido de `testDebugUnitTest` registra `ClassCastException` em `SessionRecoveryTests.protocolRecoveryOnlyStartsOnClickAndRejectsDuplicateClicks`: o teste combinava `ProfileUseCase` real com `SessionUseCase` relaxado, cuja operação genérica não executava a ação e retornava um objeto incompatível com `CompletableDeferred`.

A fixture do teste de recuperação agora usa `ProfileUseCase` explicitamente configurado, isolando a coordenação da ViewModel. As asserções também verificam que recuperação bem-sucedida solicita o perfil uma vez e que expiração de Retry-After sem ação manual não solicita o perfil. Os testes de integração entre sessão e perfil reais permanecem em `ProfileRecoveryTests` e `ProfileUseCaseTests`. Sem mudança de comportamento de produção ou contrato; correção mecânica da fixture dentro desta spec. Testes e validações não foram executados nesta correção, conforme instrução do solicitante.

### Correção da execução instrumentada interrompida

A saída de `connectedDebugAndroidTest` fornecida pelo solicitante indica queda do processo no primeiro teste de Chatbot: `AuthenticatedSessionRule` publicava sessão simulada ativa, enquanto a consulta de perfil ainda usava cliente Core/custódia reais sem access cookie correspondente. A falha de coleta de `coverage.ec` ocorreu após a queda; não comprova problema independente de cobertura nem falha nos outros 29 testes planejados, que não terminaram de executar.

As fixtures de telas autenticadas, navegação de sessão e formulário de login agora instalam perfil/foto locais vinculados à mesma sessão e restauram as dependências anteriores ao terminar, sem acesso ao Core, fotos externas ou credenciais do aparelho. O teste antigo que navegava à Home apenas ao emitir `LoginUiState.Authenticated` foi atualizado para verificar a barreira de perfil; uma consulta suspensa controlável comprova a expectativa de permanecer no carregamento até disponibilizar o perfil.

Na custódia Core, ausência do access cookie antes do envio ou da repetição passa a lançar `SessionException` (IOException recuperável), sem `IllegalArgumentException` em thread do OkHttp. A operação não é enviada sem credencial. Essa correção mantém a exigência de acesso válido e os limites de renovação existentes. Dois testes unitários de regressão foram escritos para ausência inicial e ausência após renovação.

Testes unitários/instrumentados, builds e demais validações não foram executados nesta correção, conforme orientação do solicitante. O resultado requer nova execução pelo solicitante; não há evidência de que os testes restantes da execução abortada tenham passado.

### Apontamento restante de complexidade do interceptor Core

O novo relatório Detekt de 20/10/2026, 14:47:13 UTC, contém apenas `CyclomaticComplexMethod` em `CoreSessionInterceptor.intercept` (16, limite 14). A verificação exata da origem e a construção da requisição autenticada foram extraídas em funções privadas. A leitura da credencial continua imediatamente antes de cada envio, com ausência convertida em `SessionException`; preservados descarte de Cookie, Bearer, geração, fechamento das respostas, uma repetição após renovação e bloqueio de renovação automática em falha. Refatoração mecânica sem novo comportamento, sem supressões ou alterações de regras. Detekt, testes e validações não foram executados nesta correção por instrução do solicitante.

### Integração após rebase sobre CSRF/logout

Rebase solicitado e concluído sobre `f6d58e1`, preservando os commits de perfil e suas correções, a confirmação/limpeza de logout, o CSRF de refresh/logout, o bloqueio de conteúdo durante limpeza local e o aviso de falha remota no Login. A DI mantém `UserPreferences` na sessão, além dos novos casos de uso do perfil. A Activity combina os estados de perfil e logout; o descarte de perfil/foto inclui o início de logout e não transporta uma rejeição antiga para a nova tela de Login.

Revisão do token contra Authentication de `DS_Backend/api-contract.md`: `GET profile` utiliza a base Core e o cliente `CoreHttpClient`, com access token obtido da custódia privada e enviado em `Authorization: Bearer`. Não há token no modelo de perfil, ViewModel ou persistência de perfil. Cookies, `X-CSRF-TOKEN` e `X-XSRF-TOKEN` são removidos antes do envio à origem Core; o cliente usa explicitamente `CookieJar.NO_COOKIES`. Bearer não requer CSRF no Core. Refresh e seu par CSRF permanecem exclusivamente no cliente Auth. Preservados renovação compartilhada, uma repetição após 401, verificação de geração, rejeição de resposta obsoleta e bloqueio após logout. Fotos e chatbot legado não recebem credenciais Core.

Os qualifiers Retrofit antigos associavam nomes Core/Auth a bases invertidas, embora o perfil já utilizasse seu cliente correto. O binding legado agora tem nome explícito `LegacyChatbotApi`, sem alterar seu destino ou acrescentar autenticação; foi removida a instância Retrofit sem uso que induzia confusão com o Core. Não houve alteração de contrato no backend ou implantação.

Regressões escritas/adaptadas: limpeza de rejeição/foto no logout, navegação para estados de logout e eliminação de Cookie/CSRF nas tentativas inicial/repetida ao Core, substituindo Bearer de entrada pelo access token atual. Testes, builds, Detekt, formatação automática, simulações e chamadas reais não foram executados após o rebase, conforme orientação do solicitante. A aprovação das validações informada anteriormente corresponde à árvore anterior ao rebase.

### Diagnóstico de chamadas Core e carregamento de perfil

O solicitante autorizou logs no padrão Auth. A extensão está em [Core request diagnostics](core-request-diagnostics.md): início, tentativas/status HTTP, duração, renovação, bloqueio de acesso e resultados de carregamento/validação do perfil, somente com campos sanitizados. Token, cookies, CSRF, URL, query, payload, identidade e texto/stack de exceções não são registrados. A instrumentação utiliza o Logger existente e a tag QuiStock, sem alterar fluxos de sessão/perfil. Testes de diagnóstico e ausência de dados sensíveis foram escritos, sem execução ou validação final.

### Compatibilidade do modelo tipado (21/10/2026)

A alteração do solicitante em `UserProfile` passa cargo/status para `UserRole`/`UserStatus`. O domínio e o estado de rejeição usam esses tipos; a apresentação usa `role.displayName` e a elegibilidade usa `role.hasAccess`. O rótulo de funcionário passa a “Funcionário”, conforme o enum fornecido, substituindo o rótulo anterior “Repositor”.

O DTO mantém strings na fronteira HTTP. O mapper aceita EMPLOYEE/FUNCIONARIO, MANAGER/GERENTE e REGIONAL_MANAGER/GERENTE_REGIONAL, produzindo os respectivos enums em inglês. ADMIN permanece representado como cargo sem acesso para preservar AC-17 e a mensagem de bloqueio aprovada. Status ACTIVE/INACTIVE são convertidos explicitamente; valores desconhecidos ou não canônicos falham como resposta inválida, sem fallback para cargo permitido. INACTIVE continua falhando na validação de elegibilidade.

Evidências previstas: `ProfileRoleMappingTests` para códigos novos/legados, status e valores desconhecidos; `ProfileUseCaseTests` para estado tipado e bloqueios; fixtures unitárias/instrumentadas adaptadas. Exibição do `displayName` e orientação por cargo ficam para conferência manual do solicitante. Testes e validações não serão executados por instrução explícita do solicitante.

### Compatibilidade do cargo EMPLOYEE no ambiente consultado

A resposta de GET /profile fornecida pelo solicitante contém `role=EMPLOYEE`, enquanto a documentação e `UserManagementRules` do DS_Backend local usam `FUNCIONARIO`. O modelo de domínio rejeitava o valor externo como cargo desconhecido, produzindo `reason=invalid` após HTTP 200. Não é falha do token nessa resposta. Não copiar a resposta nem identificadores/dados pessoais para documentação, fixtures ou logs.

A correção de integração converte exclusivamente o alias observado `EMPLOYEE` em `FUNCIONARIO` no mapper `ProfileResponse.toDomain`, antes da validação de domínio. O rótulo continua Repositor. Os códigos documentados permanecem compatíveis, sem conversão implícita de outros nomes em inglês, mudança de caixa ou remoção de espaços. ADMIN/GERENTE_REGIONAL continuam bloqueados; cargos desconhecidos continuam inválidos; loja ausente continua impedindo entrada; status e demais campos mantêm as regras existentes. Não há alteração no backend ou no contrato de autenticação.

Critérios adicionais: dado `EMPLOYEE` ativo com vínculo coerente, o mapper produz o papel interno FUNCIONARIO e permite a validação normal; sem loja, mantém NO_STORE; códigos antigos preservam seus resultados; cargos não reconhecidos não viram funcionário por fallback. Evidência planejada: `ProfileRoleMappingTests`, com JSON inteiramente sintético e sem dados da resposta fornecida. Testes e validações não serão executados nesta correção, conforme orientação vigente do solicitante; o ambiente real requer nova verificação manual após instalar a versão corrigida.
