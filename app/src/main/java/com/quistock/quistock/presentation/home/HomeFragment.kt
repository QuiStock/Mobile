package com.quistock.quistock.presentation.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.quistock.quistock.R
import com.quistock.quistock.databinding.FragmentHomeBinding
import com.quistock.quistock.domain.model.SessionFailure
import org.koin.androidx.viewmodel.ext.android.viewModel

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    val binding get() = requireNotNull(_binding)
    private val viewModel: HomeViewModel by viewModel()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnRetryIndicators.setOnClickListener { viewModel.retry() }
        viewModel.uiState.observe(viewLifecycleOwner, ::render)
        viewModel.load()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun render(state: HomeUiState) {
        val numbers = state.numbers
        binding.tvNumeroVencimento.text = numbers?.nearExpirationProductCount?.toString().orEmpty()
        binding.tvNumeroRuptura.text = numbers?.criticalAnalyzedFlowCount?.toString().orEmpty()
        binding.tvNumeroAcoes.text = numbers?.activeActionCount?.toString().orEmpty()

        val status = when {
            state.loading -> getString(R.string.home_indicators_loading)

            state.sessionFailure != null -> getString(
                when (state.sessionFailure) {
                    SessionFailure.NETWORK -> R.string.session_network
                    SessionFailure.TIMEOUT -> R.string.session_timeout
                    SessionFailure.SERVER -> R.string.session_server
                    SessionFailure.UNEXPECTED -> R.string.erro_login_erro_inesperado
                },
            )

            state.error -> getString(R.string.home_indicators_error)

            state.warning == HomeWarning.FUTURE_DATE ->
                getString(R.string.home_indicators_future_date_warning, state.dataDate)

            state.warning == HomeWarning.STALE ->
                getString(R.string.home_indicators_stale_warning, state.dataDate)

            else -> null
        }
        binding.tvIndicatorsStatus.text = status
        binding.tvIndicatorsStatus.visibility = if (status == null) View.GONE else View.VISIBLE
        binding.btnRetryIndicators.visibility = if (state.retryVisible) View.VISIBLE else View.GONE
        binding.btnRetryIndicators.isEnabled = state.retryEnabled
    }
}
