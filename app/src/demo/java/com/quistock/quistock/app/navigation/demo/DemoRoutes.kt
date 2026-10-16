package com.quistock.quistock.app.navigation.demo

import com.quistock.quistock.R

internal data class DemoRoute(val control: Int, val target: Int, val confirmation: Boolean = false)

internal object DemoRoutes {
    val pages = mapOf(
        R.id.onboardingFragment to listOf(
            DemoRoute(R.id.onboarding_continue_button, R.id.loginFragment),
            DemoRoute(R.id.onboarding_continue_container, R.id.loginFragment),
        ),
        R.id.loginFragment to listOf(DemoRoute(R.id.bt_entrar_login, R.id.loadingFragment)),
        R.id.cadastroPessoalFragment to listOf(
            DemoRoute(R.id.bt_prox_cadastro, R.id.loginFragment),
            DemoRoute(R.id.bt_ir_login, R.id.loginFragment),
        ),
        R.id.interferencesFragment to creation,
        R.id.managerInterferencesFragment to creation + listOf(
            DemoRoute(R.id.btnAnalisarPromocao, R.id.managerPromotionDetailFragment),
            DemoRoute(R.id.btnAnalisarEstoque, R.id.managerOrderDetailFragment),
        ),
        R.id.productControlFragment to listOf(
            R.id.cardProdutoFluxoAlto,
            R.id.cardProdutoFluxoMedio,
            R.id.cardProdutoFluxoBaixo,
            R.id.cardProdutoFluxoAlto2,
            R.id.cardProdutoFluxoMedio2,
            R.id.cardProdutoFluxoBaixo2,
        ).map { DemoRoute(it, R.id.productDetailFragment) },
        R.id.employeeFragment to listOf(
            DemoRoute(R.id.btnCadastrarFuncionario, R.id.employeeDetailFragment),
            DemoRoute(R.id.cardBrunaAlves, R.id.employeeProfileFragment),
            DemoRoute(R.id.cardRicardoMacedo, R.id.employeeProfileFragment),
            DemoRoute(R.id.cardBrenoAlmeida, R.id.employeeProfileFragment),
        ),
        R.id.employeeProfileFragment to listOf(DemoRoute(R.id.btnEditarFuncionario, R.id.editEmployeeFragment)),
        R.id.employeeDetailFragment to listOf(DemoRoute(R.id.btnCadastrar, R.id.employeeFragment)),
        R.id.editEmployeeFragment to listOf(DemoRoute(R.id.btnSalvarAlteracoes, R.id.employeeProfileFragment)),
        R.id.profileFragment to listOf(
            DemoRoute(R.id.profile_edit_button, R.id.editProfileFragment),
            DemoRoute(R.id.profile_help_row, R.id.faqFragment),
            DemoRoute(R.id.profile_sign_out_button, R.id.loginFragment),
        ),
        R.id.editProfileFragment to listOf(DemoRoute(R.id.profile_save_changes_button, R.id.profileFragment)),
        R.id.faqFragment to listOf(DemoRoute(R.id.faq_contact_link, R.id.contactFragment)),
        R.id.orderFragment to listOf(DemoRoute(R.id.btnSubmitOrder, R.id.orderSentFragment, true)),
        R.id.promotionFragment to listOf(DemoRoute(R.id.btnSubmitPromotion, R.id.promoSentFragment, true)),
        R.id.managerOrderDetailFragment to listOf(
            DemoRoute(R.id.btnAprovarPedido, R.id.managerOrderApprovedFragment, true),
        ),
        R.id.managerPromotionDetailFragment to listOf(
            DemoRoute(R.id.btnAprovarPromocao, R.id.managerPromoApprovedFragment, true),
        ),
    )

    private val creation: List<DemoRoute>
        get() = listOf(
            DemoRoute(R.id.cardPromocao, R.id.promotionFragment),
            DemoRoute(R.id.cardPedidoEstoque, R.id.orderFragment),
        )

    val backControls = listOf(
        R.id.btnVoltarControle, R.id.btnVoltarCadastro, R.id.btnCancelar,
        R.id.btnVoltarPerfilFuncionario, R.id.btnVoltarEditarFuncionario, R.id.btnCancelarEdicao,
        R.id.profile_back_control, R.id.edit_profile_back_control, R.id.profile_cancel_button,
        R.id.faq_back_control, R.id.contact_back_control, R.id.notifications_back_control,
        R.id.btnVoltarPedido, R.id.btnVoltarPromocao,
    )

    val unavailableControls = listOf(
        R.id.forgotPasswordLogin, R.id.profile_account_row, R.id.profile_store_row,
        R.id.profile_change_photo_button, R.id.contact_send_button,
        R.id.btnReprovarPedido, R.id.btnReprovarPromocao, R.id.btnRecomendacaoFefo,
        R.id.notifications_analyze_stock_button, R.id.notifications_analyze_promotion_button,
        R.id.btnEnviarPergunta, R.id.dropdownFiltroDataFuncionario, R.id.dropdownFiltroFluxo,
    )

    val completeTargets = mapOf(
        R.id.employeeDetailFragment to R.id.employeeFragment,
        R.id.editEmployeeFragment to R.id.employeeProfileFragment,
        R.id.editProfileFragment to R.id.profileFragment,
        R.id.cadastroPessoalFragment to R.id.loginFragment,
    )

    val confirmationPages = setOf(
        R.id.orderSentFragment,
        R.id.promoSentFragment,
        R.id.managerOrderApprovedFragment,
        R.id.managerPromoApprovedFragment,
    )
}
