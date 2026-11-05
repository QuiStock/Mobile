# Remover recuperação de senha da tela de login

**Status:** Complete  
**Issue:** none  
**Product decision owner:** solicitante da branch `fix/login-screen`

## Problem and goal

A tela de login mostra "Esqueceu a senha? Clique aqui.", mas o texto não tem ação: tocar nele não faz nada. O solicitante pediu a remoção da recuperação de senha dessa tela.

## Scope

**In scope:** remover o texto e o recurso de string da tela de login, mantendo o espaçamento entre o campo de senha e o botão Entrar.  
**Out of scope:** qualquer fluxo novo de recuperação de senha e alterações no Auth ou no Core.

## Expected behavior

A tela de login oferece apenas e-mail, senha, mostrar/ocultar senha, alternância de tema e Entrar. O sabor demo deixa de listar o controle removido entre os controles indisponíveis.

## Acceptance criteria and evidence

| ID | Observable given / when / then | Planned test or check |
| --- | --- | --- |
| AC-01 | Dada a tela de login aberta, então o texto de recuperação de senha não existe. | `LoginFragmentTests.passwordRecoveryIsNotOffered` |
| AC-02 | Dada a tela de login, então o botão Entrar continua separado do campo de senha pelo mesmo respiro inferior de antes (36dp). | Revisão do layout |
| AC-03 | O sabor demo não referencia mais o controle removido. | Revisão de código; `compileDemoKotlin` já falhava antes desta mudança por ids de cards removidos em `f5ce95e` |

## Technical impact

`fragment_login.xml`, `strings.xml` e `DemoRoutes.kt`. Sem migração de dados nem dependência externa.

## Decisions and open questions

| Item | Status | Source or decision owner |
| --- | --- | --- |
| Remover a recuperação de senha da tela de login | Decidido | Solicitante |

## Implementation outcome

- **AC-01:** `LoginFragmentTests.passwordRecoveryIsNotOffered` falhou antes da remoção e passou depois, em `connectedDebugAndroidTest` no aparelho físico (Android 16).
- **AC-02:** o campo de senha recebeu `layout_marginBottom="36dp"`, a margem inferior do texto removido. O botão Entrar fica mais próximo do campo porque a altura do texto e os 10dp acima dele deixaram de existir.
- **AC-03:** referência removida de `DemoRoutes.kt`. A compilação do sabor demo não pôde confirmar a mudança, pois já falha por outro motivo.
