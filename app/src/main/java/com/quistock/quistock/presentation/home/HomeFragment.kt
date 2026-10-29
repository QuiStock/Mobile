package com.quistock.quistock.presentation.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.quistock.quistock.R
import com.quistock.quistock.databinding.FragmentHomeBinding
import com.quistock.quistock.domain.model.AlertRisk
import com.quistock.quistock.domain.model.PriorityAlert
import com.quistock.quistock.domain.model.SessionState
import com.quistock.quistock.presentation.activity.SessionViewModel
import com.quistock.quistock.presentation.common.AlertProductNavigation
import com.quistock.quistock.presentation.common.PopupFeedback
import com.quistock.quistock.presentation.common.SkeletonLoadingScreen
import com.quistock.quistock.presentation.common.SkeletonScreenType
import com.quistock.quistock.presentation.profile.LogoutConfirmationDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

class HomeFragment : Fragment() {
    private var binding: FragmentHomeBinding? = null
    private val viewModel: HomeViewModel by viewModel()
    private val sessionViewModel: SessionViewModel by activityViewModel()
    private var navigating = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        SkeletonLoadingScreen.wrap(
            FragmentHomeBinding.inflate(inflater, container, false).also { binding = it }.root,
            SkeletonScreenType.HOME,
        )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val alerts = PriorityAlertsAdapter(::openAction) {
            PopupFeedback.showStatus(view, getString(R.string.home_alerts_fefo_unavailable), false)
        }
        binding?.rvAlertas?.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = alerts
        }
        binding?.alertsRetry?.setOnClickListener { viewModel.retry() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state -> render(state, alerts) }
            }
        }
        val renderer = HomeSummaryRenderer(requireContext(), requireNotNull(binding))
        renderer.render(viewModel.summary.value, false)
        binding?.summaryRetry?.setOnClickListener {
            if (sessionViewModel.session.state.value is SessionState.OfflineReadOnly) {
                sessionViewModel.retrySession()
            } else {
                viewModel.retrySummary()
            }
        }
        binding?.summaryLogout?.setOnClickListener {
            if (childFragmentManager.findFragmentByTag(LogoutConfirmationDialog.TAG) == null) {
                LogoutConfirmationDialog().showNow(childFragmentManager, LogoutConfirmationDialog.TAG)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    combine(viewModel.summary, sessionViewModel.recovery) { summary, recovery -> summary to recovery }
                        .collect { (summary, recovery) -> renderer.render(summary, recovery.retrying) }
                }
                launch {
                    while (isActive) {
                        viewModel.tickSummary()
                        delay(viewModel.nextSummaryTickMillis())
                    }
                }
            }
        }
        viewModel.enter()
        binding?.btnPerfil?.setOnClickListener {
            if (viewModel.canNavigate() && findNavController().currentDestination?.id == R.id.homeFragment) {
                findNavController().navigate(R.id.action_homeFragment_to_profileFragment)
            }
        }
    }

    override fun onDestroyView() {
        if (activity?.isChangingConfigurations != true) viewModel.leave()
        binding?.rvAlertas?.adapter = null
        binding = null
        super.onDestroyView()
    }

    override fun onStart() {
        super.onStart()
        navigating = false
    }

    private fun render(state: HomeAlertsState, adapter: PriorityAlertsAdapter) {
        val views = binding ?: return
        val ready = state as? HomeAlertsState.Ready
        views.alertsProgress.isVisible = state == HomeAlertsState.Loading
        views.alertsRetry.isVisible = state == HomeAlertsState.Error
        views.rvAlertas.isVisible = ready != null
        adapter.submitList(ready?.alerts.orEmpty())
        val message = when {
            state == HomeAlertsState.Error -> R.string.home_alerts_error
            ready?.partial == true -> R.string.home_alerts_partial
            ready != null && ready.alerts.isEmpty() -> R.string.home_alerts_empty
            else -> null
        }
        views.alertsMessage.isVisible = message != null
        message?.let { views.alertsMessage.setText(it) }
    }

    private fun openAction(alert: PriorityAlert) {
        val controller = findNavController()
        val current = viewModel.state.value as? HomeAlertsState.Ready ?: return
        val isActionable = alert in current.alerts && alert.hasPrimaryAction
        val canOpen = isActionable && !navigating && viewModel.canNavigate()
        if (!canOpen || controller.currentDestination?.id != R.id.homeFragment) return
        navigating = true
        controller.navigate(
            if (alert.risk == AlertRisk.STOCKOUT) {
                R.id.action_homeFragment_to_orderFragment
            } else {
                R.id.action_homeFragment_to_promotionFragment
            },
            AlertProductNavigation.arguments(alert.product),
        )
    }
}
