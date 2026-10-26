# Editar o próprio perfil e adicionar foto

**Status:** Ready for implementation — decisões fechadas; implementação não iniciada  
**Issue:** none  
**Product decision owner:** solicitante  
**Data:** 25/10/2026; revisada em 26/10/2026

## Problema e objetivo

Permitir que o usuário autenticado edite seu perfil e adicione uma foto pela tela Editar perfil, usando a API Core como fonte de verdade. Fonte: solicitação de 25/10/2026. Esta entrega é exclusivamente documental: não autoriza implementação Mobile ou backend, chamadas de escrita, upload ou alteração de dados reais.

## Escopo

**Incluído no planejamento:** edição do próprio perfil, adição de foto, validação, salvamento, recuperação de falhas e atualização consistente do perfil e dos headers da sessão atual.

**Fora do escopo:** editar outros usuários, e-mail, cargo/status/loja/região, senha, CRUD administrativo, alterar DS_Auth/Core, hospedar um serviço próprio de imagens e persistir o perfil em disco. Planeja-se upload direto do Android ao Cloudinary e associação da URL no Core; upload unsigned, configuração por build e adição/substituição/remoção foram aprovados nos blocos interativos.

Complementa [profile-screen.md](profile-screen.md) e [profile-user-store-data.md](profile-user-store-data.md). O comportamento atual de Salvar como aviso permanece até aprovação e implementação futura. Na implementação futura, esta spec substitui o aviso de Salvar não implementado e o descarte sem confirmação da tela estática; a etapa atual permanece documental.

## Fontes e fatos verificados

Fonte normativa: Core 3.0.0 remoto, commit d500f05682bdf92863bb2022cba5d97f8bfeb239, descrito na seção normativa abaixo. Substitui o checkout local 1.5.0 e a consulta remota 2.1.0, que ainda não ofereciam autoedição. O solicitante confirmou PATCH publicado e cargos ingleses no ambiente. Não foram realizadas chamadas ao Core implantado nesta preparação.

### Estado atual do Mobile

- `EditProfileFragment` conecta Salvar ao aviso de funcionalidade não implementada; Cancelar e Voltar retornam pela navegação existente.
- `fragment_edit_profile.xml` contém nome, e-mail e controle de foto. A existência de um campo editável ou ícone não confirma uma regra de produto ou câmera.
- `ProfileRepository` expõe somente leitura; `ProfileUseCase` compartilha o perfil em memória por sessão.
- `LoadProfilePhotoUseCase` mantém foto em memória por geração/URL. `HttpProfilePhotoRepository` baixa por HTTPS, sem credenciais Core, cookies, redirects ou cache em disco, com limite de 2 MiB. Esse limite é de download atual, não uma regra confirmada de upload.

## Comportamento esperado

### Requisitos que preservam decisões existentes

1. Trabalhar exclusivamente sobre o usuário da sessão atual, usando `PATCH /profile`. Não usar endpoints administrativos.
2. Perfil e headers devem refletir o mesmo resultado confirmado pelo servidor; prévia de foto e texto em edição não representam salvamento concluído.
3. Invalidar rascunho, prévia e resultados pendentes ao sair ou trocar de sessão; resposta tardia não pode alterar a nova sessão. Não registrar nome, e-mail, URI, URL da foto, bytes, payloads ou credenciais em logs.
4. Preservar a arquitetura Views/XML/ViewBinding/ViewModel, Koin e dependências `presentation -> domain <- data`. Tipos Android, arquivos/URIs e DTOs não pertencem ao domínio.
5. Nenhuma implementação começa enquanto qualquer O-01 a O-10 estiver Open. Fechar decisões exige fonte explícita e atualização desta spec; sugestões não contam como aprovação.

### Fluxo aprovado

- Abrir com perfil da sessão em memória, nome editável e e-mail somente leitura. Não sobrescrever rascunho por renderização. Salvar desabilitado sem mudanças.
- Oferecer galeria, câmera e adição/substituição/remoção. Cancelar seleção preserva estado sem erro; indisponibilidade/permissão preserva formulário e oferece outra origem ou configurações, sem loop.
- Aceitar JPEG/PNG/WebP estáticos até 10 MiB. Preparar JPEG até 1024 × 1024 e 2 MiB, preservando proporção, corrigindo orientação e removendo metadados, sem recorte manual.
- Só em Salvar, enviar foto nova ao Cloudinary unsigned e depois PATCH de nome alterado/URL. Falha de upload impede PATCH; foto removida envia string vazia, nunca null. Omitir campos não alterados e todos os campos não permitidos.
- Bloquear edição, novo envio e saída voluntária durante envio; logout/invalidação têm prioridade. UserDTO válido de PATCH 200 confirma sem GET obrigatório: atualizar perfil/headers da sessão, invalidar cache, voltar ao Perfil e mostrar “Perfil atualizado.”. Falha de download mantém avatar padrão sem desfazer sucesso.
- Falha definitiva Core preserva formulário e URL para tentativa manual sem novo upload da mesma seleção na mesma tela/sessão. Resultado incerto exige GET antes de nova escrita: comparar campos enviados com normalização contratual do nome; coincidência confirma estado desejado (não autoria), divergência preserva rascunho para retry manual, falha de GET oferece nova conferência sem PATCH automático. Resposta 200 incompatível não publica perfil inválido nem comprova ausência de gravação; conferir o estado.
- Upload sem retries automáticos. Core mantém renovação e uma repetição após HTTP 401 explícito; 403 bloqueia/retorna ao Login conforme fluxo existente. HTTP 429 respeita Retry-After válido, sem disparo automático ao terminar espera.
- Cancelar/Voltar/gesto confirmam descarte com alterações; sem alterações retornam diretamente. Rotação conserva rascunho/operação em memória sem reenviar. Morte do processo descarta rascunho e não retoma upload. Temporários de câmera no cache privado são limpos ao concluir, descartar/logout e na próxima abertura após interrupção.
- Cada foto nova usa ativo/URL novos, sem sobrescrita; última gravação Core vence em concorrência. Retenção de imagens antigas/removidas/órfãs e limpeza manual Cloudinary aprovadas. Remover associação não exclui ativo remoto.
- Sem configuração de upload, explicar indisponibilidade de adicionar/substituir, preservando edição de nome e remoção existente. Credenciais Core nunca vão ao Cloudinary; nenhum dado sensível vai a logs ou persistência de rascunho/perfil.

### Mensagens e acessibilidade aprovadas

Bloco 6, aprovado pelo solicitante em 26/10/2026; sucesso e conferência já aprovados anteriormente.

| Situação | Texto / ações |
| --- | --- |
| Nome vazio / longo | “Informe seu nome.” / “Use até 150 caracteres no nome.” |
| E-mail somente leitura | “O e-mail não pode ser alterado por aqui.” |
| Descarte | “Descartar alterações?”; “Continuar editando” / “Descartar” |
| Progresso | “Enviando foto…” / “Salvando perfil…” |
| Upload falho | “Não foi possível enviar sua foto. Tente novamente.” |
| Falha definitiva Core | “Não foi possível salvar seu perfil. Tente novamente.” |
| HTTP 400 | “Revise os dados do perfil e tente novamente.” |
| Resultado não confirmado | “Não foi possível confirmar o salvamento.”; oferecer nova conferência |
| Formato inválido | “Escolha uma imagem JPEG, PNG ou WebP estática.” |
| Entrada acima de 10 MiB | “Escolha uma imagem de até 10 MiB.” |
| Preparo falho | “Não foi possível preparar essa imagem. Escolha outra.” |
| Origem indisponível | “Não foi possível abrir a câmera/galeria. Use outra opção.”; usar o nome da origem acionada |
| Permissão | “Permita o acesso nas configurações ou escolha outra opção.” |
| Sem preset | “O envio de fotos está indisponível nesta versão.” |
| Sucesso | “Perfil atualizado.” |

Rotular ações “Escolher da galeria”, “Tirar foto”, “Remover foto” e “Abrir configurações”. Associar erros aos controles e anunciar erros/progresso ao leitor de tela, com suporte a fonte ampliada e teclado aberto. Preservar mensagens existentes de recuperação de sessão para 401 irrecuperável/403. Erros definitivos preservam formulário e permitem tentativa manual; resultado incerto segue conferência. Retry-After válido em 429 desabilita temporariamente retry sem disparar ao terminar a espera.

## Critérios de aceitação e evidências planejadas

Os critérios abaixo são normativos e devem ser lidos com as regras e decisões consolidadas. Nenhum teste foi escrito ou executado nesta preparação.

| ID | Dado / quando / então observável | Teste ou verificação planejada |
| --- | --- | --- |
| AC-01 | Dado perfil da sessão atual, ao abrir edição, preencher os campos aprovados sem alterar cargo/vínculos nem sobrescrever o rascunho por nova renderização | Unitário de estado e instrumentado de preenchimento; O-02/O-09 |
| AC-02 | Dados valores inválidos, Salvar não envia escrita e apresenta os erros aprovados; valores válidos geram somente os campos permitidos | Unitário de validação e teste de contrato do request; O-01/O-02/O-10 |
| AC-03 | Ao selecionar foto válida, apresentar prévia; cancelar seleção preserva o estado anterior; mídia inválida ou inacessível mostra erro sem upload | Unitário de validação e instrumentado do seletor/permissão; O-04/O-05 |
| AC-04 | Ao salvar com sucesso confirmado, atualizar perfil e headers da sessão atual, incluindo foto com URL reutilizada, e aplicar navegação/mensagem aprovada | Unitário do estado/cache e instrumentado de integração visual; O-03/O-06/O-07 |
| AC-05 | Durante envio, múltiplos toques não criam submissões concorrentes; sair/recriar tela segue a decisão sem reenviar silenciosamente | Unitário de concorrência e instrumentado de navegação/recriação; O-07/O-09 |
| AC-06 | Dada falha parcial ou timeout com resultado desconhecido, apresentar o estado real e recuperação aprovada sem afirmar rollback ou sucesso total | Testes de integração com respostas controladas e unitário da coordenação; O-06/O-08 |
| AC-07 | Dadas falhas contratuais de autorização, validação, conflito, limite de mídia, indisponibilidade e rate limit, aplicar mensagem e recuperação aprovadas sem loop | Testes de contrato/integração para os códigos efetivamente definidos; O-01/O-03/O-08/O-10 |
| AC-08 | Dado logout/troca de conta durante upload, leitura ou escrita, resultados antigos não alteram perfil, foto ou navegação da nova sessão | Unitários com concorrência controlada e instrumentado de troca de sessão |
| AC-09 | Dado transporte de foto, somente o destino autorizado recebe os headers previstos; nenhum dado sensível vai a logs ou persistência não aprovada | Teste de integração de headers e inspeção de logs/armazenamento; O-03/O-09 |
| AC-10 | Dados Cancelar, Voltar e gesto de retorno com alterações, aplicar a mesma política aprovada de descarte; sem alterações, não efetuar escrita | Instrumentado de navegação; O-07 |
| AC-11 | Dados fonte ampliada, TalkBack e teclado aberto, campos, erros, seletor e progresso permanecem compreensíveis e utilizáveis | Verificação manual assistiva/visual, justificada pela interação com UI do sistema; O-10 |
| AC-12 | Dado ambiente configurado e conta de teste autorizada, alterações persistem no Core e reaparecem numa nova sessão; e-mail permanece inalterado e cargos seguem enum inglês confirmado | Verificação integrada manual, sem dados reais na evidência; O-01/O-02/O-03 |
| AC-13 | Preset exclusivo unsigned aceita JPEG até 2 MiB, IDs únicos e sem sobrescrita, com entrega HTTPS pública; sem configuração, só adicionar/substituir ficam indisponíveis | Check manual de provisionamento Cloudinary e testes de configuração/estado; sem assumir preset já criado |
| AC-14 | Foto nova não sobrescreve ativo anterior; remover associação não apaga ativo remoto; retenção/limpeza manual e última gravação Core em concorrência são respeitadas | Integração controlada de URL/request e check manual Cloudinary |

## Impacto técnico

Prováveis áreas futuras: `presentation/profile`, layout/strings de edição, `ProfileUseCase`, portas do domínio e implementação `data/remote/core`, cache/binding de foto e DI. A necessidade de novas operações será determinada pelo contrato, sem criar camadas antecipadamente.

Não há migração, mudança de configuração ou dependência autorizada nesta etapa. O solicitante confirmou PATCH publicado e cargos ingleses em O-01; somente o repositório remoto e documentação pública foram consultados, sem acesso ao Core implantado. Dados afetados futuramente: nome e URL da foto do próprio usuário no Core, imagem no Cloudinary. E-mail permanece somente leitura; não enviar esse campo nem alterar DS_Auth.

Na implementação futura, seguir Red–Green–Refactor e executar checks pertinentes, reportando resultados reais. Cobertura local unitária não equivale ao gate combinado de CI. Não executar chamadas de escrita para resolver lacunas de contrato.

## Decisões e pontos em aberto

Todos os itens abaixo foram resolvidos pelo contrato e pelas respostas explícitas do solicitante em 26/10/2026. Provisionamento e validação integrada são verificações futuras, não decisões em aberto.

| Item | Status | Decisão necessária / responsável |
| --- | --- | --- |
| O-01 — Contrato de autoedição e ambiente | Fechado | Contrato 3.0.0 consultado. Solicitante, bloco 5 em 26/10/2026: confirma PATCH /profile já publicado e ambiente retornando EMPLOYEE, MANAGER e REGIONAL_MANAGER. Manter enum/mapeamento inglês atual, sem aliases portugueses. Confirmação do solicitante, sem chamada ao ambiente nesta preparação; validação integrada permanece evidência futura, não decisão em aberto. Usar configuração Core existente sem registrar URL privada. |
| O-02 — Campos e validação | Fechado | Core 3.0.0: apenas nome (não branco, até 150 caracteres) e URL da foto (HTTPS absoluta, sem credenciais, até 500 caracteres). Nome passa por strip no servidor após validação do tamanho. E-mail não é editável por essa API e permanece somente leitura no planejamento. Salvar sem mudanças depende de O-07. |
| O-03 — Contrato e ciclo de vida da foto | Fechado | Solicitante, blocos 1, 4 e 5 em 26/10/2026: upload direto Android → Cloudinary unsigned, sem backend/assinatura; adicionar/substituir/remover. Preset exclusivo para perfil aceita só JPEG até 2 MiB, sem sobrescrita, IDs únicos e entrega pública HTTPS. Cloud name/nome do preset vêm do build por ambiente; sem configuração, permitir nome/remoção, mas adição/substituição ficam indisponíveis com explicação. Cada foto nova usa ativo/URL novos. Remoção apaga só associação Core. Ativos antigos/removidos/órfãos são retidos com limpeza manual e possível acesso continuado pelas URLs, aceito. Core não armazena public_id nem apaga ativos. Não registrar valores ou segredos na spec. Provisionamento e compatibilidade do preset são checks de entrega futura, sem presumir configuração já realizada. |
| O-04 — Origem da imagem | Fechado | Solicitante, blocos interativos 1 e 3 em 26/10/2026: galeria e câmera; adicionar, substituir e remover. Indisponibilidade/permissão negada preserva formulário/foto e permite outra origem; explicar problema e oferecer configurações quando ajuste manual for necessário, sem repetir pedidos automaticamente. Cancelar seleção não mostra erro. Textos finais em O-10; temporários em O-09. |
| O-05 — Regras da imagem | Fechado | Solicitante, bloco interativo em 26/10/2026: JPEG, PNG e WebP estáticos de até 10 MiB na seleção; corrigir orientação, remover metadados e converter no Android para JPEG de até 1024 × 1024 e 2 MiB, preservando proporção e sem recorte manual. Limites são regras de produto aprovadas, não imposições do Core. Configuração do preset conforme O-03. |
| O-06 — Unidade de salvamento e confirmação | Fechado | Solicitante, blocos 2 a 4 em 26/10/2026: envio só em Salvar; upload de nova foto antes de PATCH conjunto nome/URL. Falha de upload impede PATCH; falha Core preserva formulário e URL para retry manual na mesma tela/sessão/seleção. UserDTO do PATCH 200 confirma e atualiza perfil/headers, invalidando cache da foto, sem GET obrigatório. Nova foto tem ativo/URL novos. Aceita última gravação processada pelo Core em concorrência, sem detecção de conflito. Órfãos têm retenção/limpeza manual aprovada em O-03. Resultado incerto segue O-08. |
| O-07 — Salvar e sair | Fechado | Solicitante, segundo bloco interativo em 26/10/2026: Cancelar, Voltar e gesto pedem confirmação de descarte quando houver alterações; sem alterações, retornam diretamente. Durante envio bloquear edição, submissão adicional e saída voluntária; logout/invalidação têm prioridade. Sucesso atualiza perfil/headers, volta ao Perfil e mostra “Perfil atualizado.”. Sem mudanças, Salvar desabilitado. Substitui o descarte sem confirmação da spec estática anterior. |
| O-08 — Erros e repetição de escrita | Fechado | Solicitante, blocos 3 e 4 em 26/10/2026: resposta perdida do PATCH exige GET antes de nova escrita. Comparar só campos enviados, normalizando nome conforme contrato: coincidência confirma estado desejado (não autoria), divergência preserva rascunho para tentativa manual, falha GET mostra “Não foi possível confirmar o salvamento.” e oferece nova conferência sem escrita automática. Upload sem retries automáticos; retry manual. Core preserva renovação existente e uma repetição apenas após HTTP 401 explícito; 403 aplica bloqueio da conta e retorno ao Login existentes. PATCH audita cada execução, sem garantia de ausência de efeitos duplicados. Textos dos demais erros em O-10. |
| O-09 — Rascunho e arquivos temporários | Fechado | Solicitante, bloco 3 em 26/10/2026: rascunho, prévia e envio sobrevivem à rotação em memória, sem duplicar requisições; morte do processo descarta rascunho e não retoma upload automaticamente. Câmera usa temporário no cache privado, limpo ao concluir, descartar ou sair da sessão; resíduos de processo interrompido são limpos na próxima abertura. Não persistir perfil/rascunho ou instalar worker de retomada. |
| O-10 — Mensagens e estados de UI | Fechado | Solicitante, bloco 6 em 26/10/2026: aprovados todos os textos, estados, ações e acessibilidade descritos na tabela final abaixo, incluindo HTTP 429 com Retry-After válido sem retry automático. |

Para fechar um item, registrar decisão, autor/fonte e data, atualizar fluxo e critérios correspondentes e marcar Fechado. Se uma capacidade não entrar na versão inicial, registrar explicitamente a exclusão aprovada. Apenas depois de todos fechados a spec poderá ser marcada Ready for implementation; esta solicitação não autoriza implementação automática após o refinamento.

## Resultado da implementação

Não iniciada. Entrega documental de 25–26/10/2026: esta spec, revisada após leitura do repositório remoto pelo conector GitHub e documentação oficial Cloudinary. Testes, Gradle, build, upload, chamadas ao Core implantado e validação visual não foram executados. Nenhum dado remoto foi alterado. Todos os pontos de decisão estão fechados; provisionamento Cloudinary e verificações práticas ainda não foram realizados.

## Revisão normativa — Core 3.0.0 e Cloudinary, 26/10/2026

Esta seção substitui os achados históricos de 25/10/2026 que afirmavam inexistência de escrita. Fonte: `main` remota, commit [`d500f05682bdf92863bb2022cba5d97f8bfeb239`](https://github.com/QuiStock/DS_Backend/commit/d500f05682bdf92863bb2022cba5d97f8bfeb239), [contrato 3.0.0 de 26/10/2026](https://github.com/QuiStock/DS_Backend/blob/d500f05682bdf92863bb2022cba5d97f8bfeb239/api-contract.md), seção 0.1.1. Conferidos também ProfileController, UpdateProfileRequest, UserProfileService e UserAccountWriteRepository nesse commit.

### Contrato confirmado

- `PATCH /profile`, JSON, sem ID de usuário enviado: altera somente a conta ativa do `sub`. A implementação usa a mesma guarda de conta ativa da leitura, sem exigir cargo administrativo.
- Campos aceitos: `name` e `profile_photo_url`. Ambos opcionais, mas ao menos um não nulo é obrigatório. Campo omitido ou null preserva o valor. Nome não pode ser branco e tem limite de 150 caracteres; servidor aplica `strip()` antes de gravar. O limite do DTO é avaliado antes desse strip.
- Foto: string com até 500 caracteres, URL absoluta HTTPS com host e sem credenciais embutidas. String exatamente vazia remove a associação e resulta em null; null não remove. O Core não baixa, valida o conteúdo da imagem ou faz upload. Não há restrição específica a Cloudinary no validador consultado.
- Campos desconhecidos são rejeitados com 400, inclusive email, senha, cargo, status e vínculos. Não serializar o perfil completo como request.
- 200 devolve UserDTO com o mesmo formato de GET /profile. A transação atualiza dados e inclui `PROFILE_UPDATED` em `audit_event`. Isso não torna upload e PATCH uma transação única nem garante idempotência de auditoria.
- Bearer isolado dispensa CSRF. Não enviar token/cookies Core ao Cloudinary. Conta ausente/inativa é rejeitada pela guarda; controller documenta 400/401/403. Não há garantia consultada de chave de idempotência ou controle de versão concorrente.

### Integração Cloudinary aprovada

1. Android seleciona/prepara a imagem e a envia diretamente ao Cloudinary com preset unsigned, conforme decisão do solicitante em 26/10/2026. Não solicitar assinatura nem alterar backend para esta entrega.
2. Com upload concluído, obtém `secure_url` e confere a compatibilidade com as regras Core e com o leitor atual (HTTPS público, sem autenticação, imagem baixada até 2 MiB). Limites aprovados em O-05.
3. Envia JSON a `PATCH /profile` com a URL e, se alterado, o nome. Para alterar só o nome, omite foto; para alterar só foto, omite nome. Remoção aprovada envia string vazia, nunca null.
4. Somente o UserDTO confirmado por 200 atualiza perfil/headers da geração atual; invalidar cache de foto. O sucesso Cloudinary sozinho não representa perfil salvo.
5. Se o upload falhar, não há URL nova a associar. Se PATCH falhar após upload, o ativo pode ficar órfão: não há rollback distribuído ou exclusão Cloudinary no Core. Aplicar retry manual, retenção e limpeza manual aprovados em O-03/O-06/O-08.

Conforme a [documentação oficial Android Cloudinary](https://cloudinary.com/documentation/android_image_and_video_upload), upload direto pode usar preset unsigned ou assinatura gerada em backend. O `api_secret` nunca deve estar no app. A resposta de upload contém URL HTTPS. O SDK pode reagendar falhas recuperáveis automaticamente: isso deve ser conciliado com cancelamento/logout e política de repetição, sem callbacks publicando em sessão antiga.

O [preset de upload](https://cloudinary.com/documentation/upload_presets) unsigned fica visível no cliente; suas restrições precisam ser definidas no Cloudinary. O solicitante descartou a dependência de assinatura por backend por restrição de prazo em 26/10/2026; unsigned é a modalidade desta entrega. O app não contém `api_secret` e não envia credenciais Core ao Cloudinary. Preset exclusivo unsigned JPEG até 2 MiB, IDs únicos, sem sobrescrita, entrega HTTPS pública e configuração por build estão aprovados; limpeza remota é manual. O preset não vincula o upload à sessão QuiStock; essa limitação deve permanecer explícita, sem alegar autorização por usuário no Cloudinary.

### Ajustes dos critérios para o contrato confirmado

- AC-02/AC-03/AC-07/AC-09/AC-12, bloco 5 de 26/10/2026: manter cargos ingleses confirmados pelo solicitante; PATCH publicado segundo sua confirmação, sem validação remota executada. Configuração de upload vem do build; ausência desabilita adição/substituição com explicação, preservando edição de nome/remoção. Verificar preset exclusivo unsigned JPEG até 2 MiB, IDs únicos, sem sobrescrita e entrega HTTPS pública em check manual de configuração e teste integrado autorizado. Provisionamento Cloudinary continua necessário antes de validar upload real, sem exigir mudanças backend.

- AC-04/AC-06/AC-07/AC-09, bloco 4 de 26/10/2026: usar UserDTO de PATCH 200 sem GET adicional obrigatório; invalidar cache e atualizar todos os headers. Cada foto nova gera ativo/URL novos. Concorrência aceita última gravação Core; não prometer controle de versão. Upload só repete por ação manual; Core renova e repete uma vez após 401 explícito, e 403 bloqueia/retorna ao Login. Testar contagem de chamadas, ausência de retry de upload e isolamento de sessão. Retenção de imagens antigas/removidas/órfãs e limpeza manual estão aprovadas; remover associação não deve acionar exclusão Cloudinary.

- AC-03/AC-11: indisponibilidade/permissão negada preserva estado, oferece outra origem e configurações quando necessário, sem loop; cancelamento não é erro. AC-05/AC-09: rotação mantém operação em memória sem reenvio; morte de processo descarta rascunho, não retoma upload e exige limpeza dos temporários privados residuais. Verificar com testes de estado/recriação e inspeção de arquivos. Decisões do terceiro bloco interativo, 26/10/2026.
- AC-06: após resposta perdida do PATCH, GET compara somente campos enviados com normalização contratual. Coincidência confirma o estado desejado; divergência preserva rascunho para tentativa manual; falha de GET oferece nova conferência com “Não foi possível confirmar o salvamento.”, sem nova escrita automática. Testar as três saídas e ausência de PATCH extra. A conferência não comprova autoria da gravação em presença de concorrência.

- AC-04/AC-05/AC-10: aprovados no segundo bloco interativo de 26/10/2026: Salvar desabilitado sem mudanças; durante envio não permitir edição, submissão duplicada ou saída voluntária, preservando prioridade de logout/invalidação. Sucesso atualiza perfil/headers, retorna ao Perfil e mostra “Perfil atualizado.”. Cancelar/Voltar/gesto confirmam descarte se houver alterações, retornando diretamente caso contrário. Cobrir por testes de estado e navegação.
- AC-06: upload começa somente em Salvar e precede PATCH de nome/URL; upload falho impede PATCH. Falha do Core preserva rascunho e URL do upload para nova tentativa manual na mesma tela/sessão e seleção, sem novo upload desnecessário. Cobrir por testes de coordenação e contagem de requisições. Resultado incerto segue a conferência aprovada em O-08.

- AC-03: galeria e câmera disponíveis; adicionar, substituir e remover foto. Validar JPEG/PNG/WebP estáticos até 10 MiB na entrada; saída JPEG até 1024 × 1024 e 2 MiB, proporção preservada, orientação corrigida e metadados removidos, sem recorte manual. Prever testes de limites, animação/formato inválido e orientação; verificar visualmente ambas as origens. Decisões aprovadas pelo solicitante em 26/10/2026. Remover associa string vazia no PATCH; não implica excluir ativo Cloudinary.

- AC-01/AC-02: e-mail somente leitura; validar nome e URL com as regras acima e enviar apenas campos permitidos. Teste de request deve cobrir omissão, null sem alteração, URL vazia para remoção e rejeição de nome branco/limites/URL inválida; não enviar e-mail.
- AC-04: usar o UserDTO retornado pelo PATCH para publicação, sem releitura obrigatória, conforme O-06 aprovado.
- AC-06/AC-07: distinguir upload de imagem, associação no Core e timeout incerto. Cobrir upload bem-sucedido seguido de PATCH falho, sem sucesso total fictício ou segundo upload desnecessário; política aprovada em O-06/O-08.
- AC-12: verificar em homologação nome/foto por nova sessão; e-mail deve permanecer inalterado. PATCH publicado e cargos ingleses confirmados pelo solicitante; validação integrada ainda não executada.

