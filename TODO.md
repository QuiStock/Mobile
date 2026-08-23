# Próximos passos do projeto Mobile

Este documento começa a valer depois da conclusão das três PRs de preparação:

1. baseline de build e qualidade;
2. defaults de produção;
3. CI e processo de TDD.

Os itens abaixo não devem ser implementados apenas porque aparecem nesta lista. Cada um possui um critério explícito de entrada para evitar complexidade prematura. Quando o critério for atendido, a implementação deve entrar em uma PR própria ou junto da primeira funcionalidade que realmente a exigir.

## Regra geral para novas funcionalidades

### Critério para iniciar

- Existe uma história, requisito ou correção de bug real a ser implementada.

### Implementar

- Desenvolver seguindo o ciclo Red-Green-Refactor.
- Testar resultados observáveis, caminhos de sucesso e erros relevantes.
- Manter pelo menos 80% de cobertura de linhas no código considerado pelo JaCoCo.
- Não criar exclusões de cobertura para fazer o gate passar.
- Atualizar documentação quando a mudança afetar setup, arquitetura ou operação.

### Concluído quando

- Os testes falham sem a implementação ou com o comportamento deliberadamente quebrado.
- `spotlessCheck`, `lintDebug`, `jacocoTestCoverageVerification` e `assembleRelease` passam.
- A PR explica o comportamento entregue e os testes adicionados.

## 1. Definir a arquitetura das funcionalidades reais

### Critério para iniciar

- A primeira funcionalidade real exigir mais do que uma tela com estado local, por exemplo acesso a rede, persistência, autenticação ou uma regra de negócio reutilizável.

### Implementar

- Separar responsabilidades de UI, estado, casos de uso e acesso a dados.
- Definir interfaces apenas nos pontos em que exista uma dependência externa ou mais de uma implementação útil.
- Adotar injeção de dependências somente quando a criação manual dos objetos começar a dificultar testes ou composição.
- Registrar as decisões relevantes em uma seção de arquitetura no README ou em um ADR curto.

### Concluído quando

- Regras de negócio podem ser testadas sem Activity, Fragment, rede ou banco reais.
- A UI não contém regras de negócio ou acesso direto à infraestrutura.
- A estrutura possui uma justificativa baseada em funcionalidades existentes, e não em possíveis necessidades futuras.

## 2. Adicionar testes instrumentados dos fluxos críticos

### Critério para iniciar

- O primeiro fluxo real completo puder ser executado por um usuário, como cadastrar produto, consultar estoque ou registrar uma movimentação.

### Implementar

- Criar pelo menos um teste instrumentado para o caminho principal do fluxo.
- Cobrir navegação, interação e resultado visível para o usuário.
- Usar dados controlados e evitar dependência de serviços externos instáveis.
- Executar os testes instrumentados na `main`, manualmente antes das entregas ou em job agendado, caso o emulador seja caro para toda PR.

### Concluído quando

- O teste falha se o fluxo principal for quebrado.
- O teste pode ser repetido sem depender da ordem dos testes ou de dados deixados por uma execução anterior.
- A equipe possui um comando documentado para executá-lo localmente.

## 3. Adicionar cobertura de branches

### Critério para iniciar

- Existirem regras de negócio reais com decisões relevantes, como validações, estados de estoque, permissões ou tratamento de resultados de rede.

### Implementar

- Adicionar ao JaCoCo uma meta inicial de 70% de cobertura de branches.
- Manter 80% de cobertura de linhas.
- Testar caminhos de sucesso, falha e limites relevantes, sem criar testes apenas para executar branches impossíveis ou sem valor.

### Concluído quando

- A meta passa com testes comportamentais compreensíveis.
- Cada decisão crítica possui testes para os resultados relevantes.
- Casos deliberadamente não cobertos estão justificados na PR, sem exclusões genéricas de pacotes.

## 4. Revisar persistência e política de backup

### Critério para iniciar

- O aplicativo começar a persistir preferências, banco local, arquivos, sessão ou qualquer dado criado pelo usuário.

### Implementar

- Classificar os dados em restauráveis, temporários, sensíveis e específicos do dispositivo.
- Manter tokens, credenciais, caches e identificadores de dispositivo fora do backup.
- Continuar com backup desativado se nenhum dado precisar ser restaurado.
- Caso o backup tenha valor real, trocar a proibição total por regras explícitas de inclusão e exclusão.
- Testar restauração e troca de dispositivo antes de habilitar backup em um release.

### Concluído quando

- Existe uma lista documentada dos dados incluídos e excluídos.
- Nenhum segredo, sessão ou identificador específico do dispositivo é restaurado.
- O comportamento após reinstalação ou restauração é previsível e testado.

## 5. Separar ambientes Firebase

### Critério para iniciar

Implementar um segundo ambiente Firebase quando pelo menos uma destas condições ocorrer:

- usuários externos começarem a testar o aplicativo;
- pedidos, clientes, estoque ou credenciais reais forem armazenados;
- dados de desenvolvimento puderem atrapalhar uma demonstração ou avaliação;
- regras, índices ou configurações experimentais puderem afetar dados que precisam ser preservados;
- métricas de debug e release precisarem ser analisadas separadamente.

### Implementar

- Criar no mínimo os ambientes `dev` e `prod`.
- Adicionar product flavors somente nesse momento.
- Manter arquivos `google-services.json` nos respectivos source sets.
- Garantir que builds de desenvolvimento não escrevam no ambiente de produção.
- Documentar como cada integrante obtém as configurações permitidas.

### Concluído quando

- É impossível instalar um build de desenvolvimento configurado acidentalmente para gravar em produção.
- Dados e métricas dos ambientes podem ser identificados separadamente.
- Ambos os variants compilam no CI.

## 6. Endurecer regras do backend

### Critério para iniciar

- O aplicativo usar Firebase Authentication, Firestore, Realtime Database, Storage, Functions ou outro backend com dados acessíveis pela rede.

### Implementar

- Aplicar princípio de menor privilégio nas regras de acesso.
- Não confiar em validações feitas apenas no aplicativo.
- Criar testes das regras para acessos permitidos e negados.
- Definir propriedade dos dados e papéis necessários, como funcionário e administrador, somente se o requisito existir.

### Concluído quando

- Usuários não autenticados e usuários sem permissão não conseguem ler ou alterar dados protegidos.
- As regras possuem testes automatizados ou um roteiro reproduzível de validação.
- Nenhuma regra ampla temporária, como acesso público total, permanece ativa.

## 7. Preparar assinatura, AAB e distribuição

### Critério para iniciar

- Houver decisão de distribuir o aplicativo fora dos dispositivos da equipe, inclusive para professores, clientes simulados ou testadores externos.

### Implementar

- Gerar Android App Bundle para distribuição.
- Criar e proteger uma upload key.
- Manter keystore e senhas fora do Git e armazená-los em local seguro.
- Definir `versionCode` monotônico e `versionName` relacionado a tags.
- Preservar mapping do R8 e símbolos necessários para diagnosticar falhas.
- Criar um checklist manual de release antes de automatizar publicação.

### Concluído quando

- Um integrante autorizado consegue reproduzir um build assinado seguindo a documentação.
- O artefato pode ser atualizado sem trocar a identidade ou a chave esperada do aplicativo.
- A equipe consegue relacionar artefato, código-fonte, versão e mapping do R8.

## 8. Ampliar a matriz de testes Android

### Critério para iniciar

- O código começar a variar por versão do Android, usar permissões, notificações, tarefas em segundo plano, câmera, arquivos ou outra API com diferenças relevantes entre versões; ou surgirem falhas específicas de dispositivo.

### Implementar

- Testar ao menos no `minSdk` suportado e no `targetSdk` atual.
- Adicionar versões intermediárias somente quando houver comportamento específico a validar.
- Priorizar no CI os fluxos críticos, deixando uma matriz maior para execução agendada ou antes da entrega.

### Concluído quando

- O fluxo afetado passa no `minSdk` e no `targetSdk`.
- Diferenças de comportamento entre versões possuem testes ou documentação explícita.
- A duração da matriz continua aceitável para a equipe.

## 9. Automatizar atualização e verificação de dependências

### Critério para iniciar

- O projeto possuir várias dependências diretas, atualizações manuais começarem a ser esquecidas ou uma atualização causar incompatibilidade difícil de diagnosticar.

### Implementar

- Configurar Dependabot ou Renovate com frequência semanal.
- Manter atualizações grandes e mudanças de AGP, Gradle ou Kotlin em PRs separadas.
- Adicionar dependency verification ou locking se versões transitivas começarem a variar entre máquinas ou builds.
- Nunca aceitar atualizações automaticamente sem executar o pipeline completo.

### Concluído quando

- Atualizações chegam por PRs revisáveis com testes e release build passando.
- A equipe consegue identificar por que uma versão foi atualizada ou mantida.
- Builds locais e do CI resolvem o mesmo conjunto esperado de dependências.

## 10. Introduzir observabilidade real

### Critério para iniciar

- O aplicativo for usado fora da equipe e falhas não puderem mais ser reproduzidas apenas com testes locais.

### Implementar

- Confirmar Crashlytics somente nos builds de distribuição.
- Evitar dados pessoais, credenciais ou conteúdo sensível em logs e eventos.
- Definir eventos de Analytics apenas para perguntas relevantes sobre uso do aplicativo.
- Criar uma rotina simples de revisão de crashes, ANRs e regressões antes de cada entrega.

### Concluído quando

- Uma falha de release pode ser relacionada à versão e deofuscada.
- Os eventos possuem nome, finalidade e dados permitidos documentados.
- A equipe sabe quem verifica os relatórios e quando uma falha bloqueia uma entrega.

## 11. Revisar acessibilidade e internacionalização

### Critério para iniciar

- A primeira tela real destinada ao usuário for implementada.

### Implementar

- Manter textos visíveis em recursos, sem strings hardcoded.
- Usar formatação de números, moedas e datas conforme locale.
- Verificar contraste, modo escuro, áreas de toque e aumento de fonte.
- Adicionar descrições acessíveis somente onde o componente não possuir texto ou semântica suficiente.
- Testar navegação básica com leitor de tela antes da entrega final.

### Concluído quando

- Android Lint não aponta novos problemas relevantes de internacionalização ou acessibilidade.
- O fluxo continua utilizável com fonte ampliada e tema claro/escuro.
- Valores de loja, como moeda e quantidade, são apresentados de forma consistente com o locale escolhido.

## 12. Avaliar modularização

### Critério para iniciar

- Existirem pelo menos três áreas funcionais relativamente independentes, conflitos frequentes nos mesmos arquivos de build ou tempos de build que prejudiquem o trabalho da equipe.

### Implementar

- Medir primeiro o problema de build ou acoplamento.
- Extrair um módulo por vez, começando por uma área com fronteira clara.
- Evitar módulos criados apenas para reproduzir uma estrutura de projeto empresarial.
- Preservar testes e dependências direcionadas durante a extração.

### Concluído quando

- A extração reduz acoplamento, conflitos ou tempo de build de forma observável.
- O novo módulo possui responsabilidade clara e API pequena.
- A complexidade de navegação e configuração não supera o benefício obtido.

## 13. Avaliar Baseline Profiles e testes de desempenho

### Critério para iniciar

- Existirem fluxos reais estáveis e houver lentidão perceptível, jank, inicialização demorada ou preparação para uma distribuição mais ampla.

### Implementar

- Medir antes de otimizar.
- Criar benchmarks para inicialização e interações críticas.
- Gerar Baseline Profile somente para jornadas reais e estáveis.
- Comparar resultados antes e depois em dispositivo ou ambiente de teste consistente.

### Concluído quando

- Existe uma métrica anterior que justifica a otimização.
- O ganho é mensurável e o profile representa fluxos realmente usados.
- O processo de regeneração está documentado e pode ser repetido antes de releases relevantes.

## 14. Revisar a política de qualidade periodicamente

### Critério para iniciar

- A cada marco de entrega ou quando o gate de qualidade começar a falhar repetidamente sem indicar defeitos reais.

### Implementar

- Revisar cobertura, exclusões, duração do CI, falhas instáveis e utilidade dos testes.
- Aumentar rigor quando houver valor demonstrável, especialmente em regras críticas.
- Corrigir testes frágeis em vez de ignorá-los.
- Registrar qualquer redução de rigor com motivo, prazo e responsável pela revisão.

### Concluído quando

- Os gates continuam rápidos o suficiente para serem executados em toda PR.
- Falhas do pipeline indicam problemas acionáveis.
- As exceções permanecem poucas, explícitas e justificadas.
