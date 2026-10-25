package com.quistock.quistock.app.navigation.demo

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.navOptions
import com.quistock.quistock.R
import com.quistock.quistock.presentation.common.PopupFeedback
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Offline presentation of the shared layouts. Real feature fragments are never instantiated:
 * no session, ViewModel, repository or persistence is used by this build.
 */
class DemoPageFragment : Fragment() {
    private val activity get() = requireActivity() as DemoActivity
    private val nav get() = findNavController()
    private var page = 0
    private var navigating = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflateDemoPage(requireArguments().getInt("layout"), inflater, container).root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        page = nav.currentDestination!!.id
        DemoRoutes.pages[page].orEmpty().forEach { route ->
            click(route.control) {
                if (route.confirmation) {
                    PopupFeedback.showConfirmation(requireContext(), getString(R.string.demo_confirm)) {
                        open(route.target)
                    }
                } else {
                    open(route.target)
                }
            }
        }
        DemoRoutes.backControls.forEach { click(it) { nav.popBackStack() } }
        DemoRoutes.unavailableControls.forEach { click(it) { unavailable() } }
        click(R.id.btnVoltarInterferencias) { returnTo(interferences()) }
        setupTabs()
        setupHeaders(view)
        setupLocalInteractions(view)
        setupEntry(view)
    }

    override fun onResume() {
        super.onResume()
        navigating = false
    }

    private fun setupEntry(view: View) {
        if (page == R.id.loginFragment) {
            val enter = view.findViewById<View>(R.id.bt_entrar_login)
            val density = resources.displayMetrics.density
            (enter.parent as ViewGroup).addView(
                Button(requireContext()).apply {
                    id = R.id.demo_register
                    setText(R.string.demo_register)
                    setBackgroundResource(R.drawable.bg_btn_action_reject)
                    setTextColor(android.graphics.Color.WHITE)
                    textSize = 15f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    isAllCaps = false
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        (52 * density).toInt(),
                    ).apply {
                        topMargin = (12 * density).toInt()
                    }
                    setOnClickListener { open(R.id.cadastroPessoalFragment) }
                },
            )
        }
        val target = when (page) {
            R.id.splashFragment -> R.id.onboardingFragment
            R.id.loadingFragment -> R.id.homeFragment
            else -> return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                delay(TRANSITION_DELAY)
                open(target)
            }
        }
    }

    private fun setupTabs() {
        val tabs = mapOf(
            R.id.homeFragment to listOf(R.id.btnHome, R.id.btnHomeNav, R.id.btnHomeFuncionarios, R.id.btnHomeChatbot),
            interferences() to listOf(
                R.id.btnMovimentacoes,
                R.id.btnInterferenciasNav,
                R.id.btnMovimentacoesFuncionarios,
                R.id.btnInterferenciasChatbot,
            ),
            R.id.employeeFragment to listOf(
                R.id.btnFuncionarios,
                R.id.btnFuncionariosNav,
                R.id.btnControleFuncionariosAtivo,
            ),
            R.id.productControlFragment to listOf(R.id.btnProdutos, R.id.btnProdutosChatbot),
            R.id.notificationsFragment to listOf(
                R.id.btnNotificacoesBottom,
                R.id.btnNotificacoesNav,
                R.id.btnNotificacoesBottomFuncionarios,
            ),
            R.id.chatbotFragment to listOf(
                R.id.btnFolha,
                R.id.btnChatbotNav,
                R.id.btnChatbotFuncionarios,
                R.id.btnChatbotAtivo,
            ),
        )
        tabs.forEach { (target, controls) -> controls.forEach { click(it) { openTab(target) } } }
    }

    private fun setupHeaders(view: View) {
        // Several original header icons have descriptions but no resource ID.
        if (view.contentDescription?.toString() == "Perfil") view.setOnClickListener { open(R.id.profileFragment) }
        if (view.contentDescription?.toString() == "Notificações") {
            view.setOnClickListener { open(R.id.notificationsFragment) }
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) setupHeaders(view.getChildAt(index))
        }
        click(R.id.btnPerfil) { open(R.id.profileFragment) }
        listOf(
            R.id.btnNotificacoes,
            R.id.btnNotificacoesChatbot,
            R.id.btnNotificacoesFuncionarios,
            R.id.btnNotificacoesInterferencias,
            R.id.btnNotificacoesPedido,
            R.id.btnNotificacoesPromocao,
        ).forEach { click(it) { open(R.id.notificationsFragment) } }
    }

    private fun setupLocalInteractions(view: View) {
        listOf(
            Triple(R.id.faq_card_1, R.id.faq_answer_1, R.id.faq_arrow_1),
            Triple(R.id.faq_card_2, R.id.faq_answer_2, R.id.faq_arrow_2),
            Triple(R.id.faq_card_3, R.id.faq_answer_3, R.id.faq_arrow_3),
            Triple(R.id.faq_card_4, R.id.faq_answer_4, R.id.faq_arrow_4),
        ).forEach { (card, answer, arrow) ->
            click(card) {
                val text = view.findViewById<View>(answer)
                text.visibility = if (text.visibility == View.GONE) View.VISIBLE else View.GONE
                view.findViewById<ImageView>(arrow).setImageResource(
                    if (text.visibility == View.VISIBLE) R.drawable.arrow_up else R.drawable.arrow_down,
                )
            }
        }
        click(R.id.btnMenuChatbot) { view.findViewById<View>(R.id.chatMenuOverlay).visibility = View.VISIBLE }
        click(R.id.chatMenuOverlay) { view.findViewById<View>(R.id.chatMenuOverlay).visibility = View.GONE }
        click(R.id.chatMenuPanel) { }
        click(R.id.btnNewChat) {
            view.findViewById<LinearLayout>(R.id.containerMensagens).removeAllViews()
            view.findViewById<EditText>(R.id.etPerguntaChatbot).text.clear()
            view.findViewById<View>(R.id.chatMenuOverlay).visibility = View.GONE
        }
    }

    private fun click(id: Int, action: () -> Unit) {
        view?.findViewById<View>(id)?.setOnClickListener {
            if (nav.currentDestination?.id == page && !navigating) action()
        }
    }

    private fun unavailable() =
        PopupFeedback.showStatus(requireView(), getString(R.string.demo_unavailable), isError = false)

    private fun interferences(): Int =
        if (activity.manager) R.id.managerInterferencesFragment else R.id.interferencesFragment

    private fun openTab(target: Int) {
        if (target == page) return
        navigating = true
        nav.popBackStack(R.id.homeFragment, false)
        if (target != R.id.homeFragment) nav.navigate(target, null, navOptions { launchSingleTop = true })
    }

    private fun returnTo(target: Int) {
        navigating = true
        if (!nav.popBackStack(target, false)) {
            nav.navigate(
                target,
                null,
                navOptions {
                    popUpTo(R.id.homeFragment)
                    launchSingleTop = true
                },
            )
        }
    }

    private fun open(target: Int) {
        if (navigating || nav.currentDestination?.id != page) return
        if (!viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        if (DemoRoutes.completeTargets[page] == target) {
            returnTo(target)
            return
        }
        navigating = true
        nav.navigate(
            target,
            null,
            navOptions {
                launchSingleTop = true
                when {
                    target == R.id.homeFragment || (page == R.id.profileFragment && target == R.id.loginFragment) ->
                        popUpTo(nav.graph.id) { inclusive = false }

                    page == R.id.splashFragment || page == R.id.onboardingFragment ||
                        DemoRoutes.confirmationPages.contains(target) -> popUpTo(page) { inclusive = true }
                }
            },
        )
    }

    private companion object {
        const val TRANSITION_DELAY = 900L
    }
}
