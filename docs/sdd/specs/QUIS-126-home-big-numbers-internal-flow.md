# Big numbers na página principal: fluxo interno sem API

**Status:** Implemented; verification pending

**Issue:** [QUIS-126 — Visualizar Big Numbers na home page do app](https://quistock.atlassian.net/browse/QUIS-126)

**Product decision owner:** Solicitante (decisões registradas nesta conversa)

## Problem and goal

O pedido desta etapa é concluir a lógica interna que apresenta os três indicadores na página principal antes de a API estar disponível. Hoje `fragment_home.xml` contém três números fixos e `HomeFragment` apenas infla o layout. Já existem `BigNumbers`, `RefreshBigNumbersUseCase`, cache Room e uma fonte simulada ligada por Koin, mas o resultado não chega à tela. O objetivo é mostrar valores provenientes desse fluxo, inclusive seus estados de indisponibilidade, sem depender de uma integração real.

## Scope

**In scope:** ligar a Home ao caso de uso existente por um ViewModel; representar e renderizar carregamento, dados atuais, dados antigos e ausência de dados; substituir os números fixos por valores do estado; manter a fonte simulada para exercício local; testar o fluxo interno.

**Out of scope:** Retrofit/contrato da API, autenticação ou dados por loja, cálculo dos indicadores a partir de produtos ou movimentações, classificação de fluxo pelo modelo de Machine Learning, lista de alertas, cabeçalho, navegação e ações dos cards.

## Expected behavior

Sempre que a View do `HomeFragment` for construída para exibição, o ViewModel executa o caso de uso de carregar big numbers e expõe um estado de carregamento com o texto “Carregando indicadores…” até a conclusão. Isso inclui a volta à Home quando a View for construída novamente e a recriação da View. Se houver cache da data de hoje, ele é considerado atualizado e os três valores são exibidos normalmente, sem indicar que vieram do cache. Se não houver cache de hoje, o fluxo tenta buscar novos valores. Nesta etapa, essa busca usa a fonte simulada; a API futura recalculará o dado uma vez por dia. Se a busca falhar, o dado mais recente do cache é exibido, quando existir. Se ele for anterior a hoje, a tela avisa que pode haver divergências e informa a data do dado exibido. Se a data do cache for posterior à data indicada pelo dispositivo, ela é considerada não confiável: o fluxo tenta uma busca e, se ela falhar, mantém os valores do cache com aviso de possíveis divergências, a data registrada e indicação de que a data do dispositivo pode estar incorreta. O cache não é apagado por essa condição. Sem dado obtido e sem cache disponível, a tela apresenta “Não foi possível carregar os indicadores. Verifique sua conexão e tente novamente.”, oferece “Tentar novamente” e não exibe os números de exemplo do XML. O botão “Tentar novamente” também aparece quando há cache anterior ou com data posterior à do dispositivo; ele não aparece para cache de hoje. Uma nova tentativa executa novamente o caso de uso. Se houver números em cache, eles permanecem visíveis durante a tentativa e, se ela falhar, permanecem com o aviso. Durante a tentativa, o botão fica desabilitado para evitar buscas simultâneas.

O caso de uso já cobre o cache anterior e ausente: cache anterior aciona a fonte simulada; falha da busca retorna esse cache; sem cache, retorna falha. A comparação atual, `createdAt >= meia-noite de hoje`, também aceita um timestamp futuro como atual. Nesta etapa ela deve passar a aceitar apenas dados da data corrente no fuso do dispositivo, para que cache futuro acione a busca e, se necessário, o fallback com aviso. Uma falha ao salvar não impede a apresentação dos valores obtidos. A fonte simulada contém números de demonstração, que não representam dados reais de uma loja.

A correspondência entre `nearExpirationProductCount` e o card “Vencimento curto”, e entre `activeActionCount` e “Ações ativas”, segue os nomes existentes. Por decisão de produto informada nesta conversa, `criticalAnalyzedFlowCount` alimenta o card “Ruptura/Excesso”. “Fluxo crítico” é o termo interno para tudo que está fora do fluxo ideal, chamado “fluxo médio”. A classificação é feita por um modelo de Machine Learning; o Mobile recebe e exibe o indicador, sem reproduzir essa lógica.

## Acceptance criteria and evidence

| ID | Observable given / when / then | Planned test or check |
| --- | --- | --- |
| AC-01 | Given a Home aberta, when a solicitação começa, then a tela indica carregamento e não apresenta números fixos como se fossem dados obtidos. | Teste de ViewModel para a transição; teste instrumentado ou checagem manual do layout, pois depende de Views. |
| AC-02 | Given `UpToDate`, when o estado é renderizado, then “Vencimento curto” mostra `nearExpirationProductCount`, “Ruptura/Excesso” mostra `criticalAnalyzedFlowCount` e “Ações ativas” mostra `activeActionCount`, inclusive quando algum valor é zero. | Teste de ViewModel para mapeamento; teste instrumentado dos textos dos cards. |
| AC-03 | Given cache da data de hoje, when a Home solicita os indicadores, then os valores do cache são exibidos como atualizados, sem nova busca nem aviso de desatualização. | Teste unitário existente do caso de uso; teste de ViewModel e checagem instrumentada da tela. |
| AC-04 | Given ausência de cache de hoje, when a Home solicita os indicadores, then a fonte é consultada; se falhar e houver cache anterior, os valores mais recentes do cache aparecem com aviso de possíveis divergências e a data do dado exibido. | Teste unitário do caso de uso, teste de ViewModel para estado e data, e teste instrumentado ou checagem manual do aviso e acessibilidade. |
| AC-04A | Given cache com data posterior à data do dispositivo, when a Home solicita os indicadores, then o cache não é tratado como atual e ocorre uma busca; se ela falhar, o cache é preservado e exibido com aviso de possíveis divergências, sua data registrada e indicação de possível incorreção da data do dispositivo. | Teste unitário do caso de uso para a busca e o fallback; teste de ViewModel e teste instrumentado ou checagem manual do aviso. |
| AC-05 | Given falha da busca e ausência de cache, when o estado é renderizado, then nenhum número de demonstração aparece e uma mensagem de erro amigável é apresentada. | Teste de ViewModel e teste instrumentado ou checagem manual da tela. |
| AC-06 | Given erro sem dados, cache anterior ou cache com data posterior à do dispositivo, when a pessoa aciona “Tentar novamente”, then o caso de uso é executado novamente e o botão fica desabilitado durante a tentativa; toques repetidos não criam buscas simultâneas. | Testes de ViewModel para os três estados e chamadas simultâneas; teste instrumentado ou checagem manual do botão. |
| AC-06A | Given números em cache, when uma nova tentativa começa ou falha, then os números permanecem visíveis e, na falha, o aviso correspondente continua exibido; quando a tentativa funciona, o novo resultado substitui os valores. | Teste de ViewModel e teste instrumentado ou checagem manual da tela. |
| AC-07 | Given uma nova construção da View do `HomeFragment`, inclusive após recriação, when ela é preparada para exibição, then o caso de uso é executado novamente; com cache de hoje, essa execução não busca novos dados na fonte. | Teste instrumentado do ciclo de vida do Fragment e teste unitário do caso de uso com cache de hoje. |
| AC-08 | Given cache da data corrente, anterior, futura ou ausente, when o caso de uso executa com sucesso ou falha da fonte simulada, then ele produz `UpToDate`, `Stale` ou `Failed` conforme a política diária. | Testes unitários existentes de `RefreshBigNumbersUseCase` e novos testes para cache futuro. |
| AC-09 | Given a fonte simulada configurada, when a Home é aberta sem API disponível, then o fluxo completo pode exibir os três valores sem rede. | Teste de DI e checagem manual ou instrumentada da Home com fonte simulada. |

## Technical impact

Prováveis alterações em `presentation/home` (`HomeFragment`, novo estado e ViewModel), `fragment_home.xml`, recursos de texto, `PresentationModule` e testes correspondentes. `RefreshBigNumbersUseCase`, portas de domínio, implementação Room e `MockRemoteBigNumbersRepository` já existem; alterá-los apenas se os testes mostrarem uma necessidade do fluxo aprovado. Sem migração de dados prevista. Sem endpoint, credenciais ou dependência de rede para estes indicadores nesta etapa. A persistência local existente continua sendo o cache afetado. Os valores simulados servem à validação da branch; por decisão do usuário, esta PR só será mergeada quando a API estiver disponível, então a UI não precisa de uma identificação de demonstração nesta etapa.

## Decisions and open questions

| Item | Status | Source or decision owner |
| --- | --- | --- |
| `criticalAnalyzedFlowCount` alimenta “Ruptura/Excesso”; “fluxo crítico” reúne o que está fora do fluxo ideal (“fluxo médio”). | Decidido | Usuário, nesta conversa. |
| A classificação de fluxo é feita por um modelo de Machine Learning; o Mobile apenas exibe o indicador recebido. | Decidido | Usuário, nesta conversa. |
| Cache de hoje equivale a dado atualizado; sem cache de hoje há busca; na falha, mostrar o cache mais recente com aviso de possíveis divergências e data; sem dado, mostrar erro amigável. | Decidido | Usuário, nesta conversa. |
| Cache futuro em relação ao dispositivo aciona busca; se ela falhar, os valores permanecem com aviso, data registrada e possível problema no relógio, sem apagar o cache. | Decidido provisoriamente até existir contrato da API | Usuário, nesta conversa. |
| Data exibida nos avisos no formato `dd/MM/yyyy`. | Decidido | Usuário, nesta conversa. |
| Aviso quando a busca falha e há dados anteriores: “Pode haver um problema com sua conexão. Exibindo dados de dd/MM/yyyy, que podem estar desatualizados.” | Decidido | Usuário, nesta conversa. |
| Aviso quando a busca falha e o cache tem data posterior à do dispositivo: “A data e a hora do dispositivo podem estar incorretas. Exibindo dados de dd/MM/yyyy, que podem apresentar divergências.” | Decidido | Usuário, nesta conversa. |
| Carregamento: “Carregando indicadores…”. Erro sem dados: “Não foi possível carregar os indicadores. Verifique sua conexão e tente novamente.” Botão: “Tentar novamente”. | Decidido | Usuário, nesta conversa. |
| Executar o caso de uso sempre que a View do `HomeFragment` for construída para exibição; cache de hoje evita nova busca na fonte. | Decidido | Usuário, nesta conversa. |
| Oferecer “Tentar novamente” após erro sem dados, com cache anterior ou com data posterior à do dispositivo; manter números em cache visíveis durante a tentativa; desabilitar o botão até a conclusão; não oferecer o botão com cache de hoje. | Decidido | Usuário, nesta conversa. |
| Não identificar os valores simulados na UI nesta etapa; a PR só será mergeada quando a API estiver disponível. | Decidido | Usuário, nesta conversa. |

## Implementation outcome

- AC-01 a AC-09: lógica de atualização diária, estados da Home, avisos, nova tentativa, ciclo de vida da View e fonte simulada ligados conforme os critérios acima.
- Foram adicionados testes unitários para cache futuro e estados do ViewModel, e testes instrumentados para renderização e recriação da Home. Os testes existentes do caso de uso e da injeção de dependências continuam relevantes.
- Testes, build e verificações manuais ainda não foram executados nesta alteração, a pedido do solicitante, enquanto o Gradle do ambiente está indisponível. Os resultados permanecem **não verificados** até essa execução.
