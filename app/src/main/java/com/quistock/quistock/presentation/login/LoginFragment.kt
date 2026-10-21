package com.quistock.quistock.presentation.login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.quistock.quistock.R
import com.quistock.quistock.app.navigation.NavGraph
import com.quistock.quistock.databinding.FragmentLoginBinding
import com.quistock.quistock.domain.model.LoginError
import com.quistock.quistock.presentation.profile.LogoutRemoteWarningDialog
import org.koin.androidx.viewmodel.ext.android.viewModel

class LoginFragment : Fragment() {
    private var _binding: FragmentLoginBinding? = null
    val binding get() = _binding!!

    private val viewModel: LoginViewModel by viewModel()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLoginBinding.inflate(
            inflater,
            container,
            false,
        )

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupListeners()
        if (arguments?.getBoolean("expired") == true) viewModel.showExpired()
        if (arguments?.getBoolean("unexpected") == true) viewModel.showUnexpectedError()
        val profileMessage = arguments?.getInt("profile_rejection_message", 0) ?: 0
        arguments?.remove("expired")
        arguments?.remove("unexpected")
        observeState()
        if (arguments?.getBoolean(LogoutRemoteWarningDialog.ARGUMENT) == true) {
            if (childFragmentManager.findFragmentByTag(LogoutRemoteWarningDialog.TAG) == null) {
                LogoutRemoteWarningDialog().show(childFragmentManager, LogoutRemoteWarningDialog.TAG)
            }
            arguments?.remove(LogoutRemoteWarningDialog.ARGUMENT)
        }
        if (profileMessage != 0) {
            binding.txtErroLogin.setText(profileMessage)
            binding.txtErroLogin.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun login() {
        val email = binding.emailLogin.text.toString()
        val password = binding.senhaLogin.text.toString()
        if (email.isBlank() || password.isBlank()) {
            com.quistock.quistock.presentation.common.PopupFeedback.showStatus(
                binding.root,
                getString(R.string.popup_login_invalid),
                isError = true,
            )
            return
        }

        viewModel.authenticate(email = email, password = password)
    }

    private fun setupListeners() {
        binding.btEntrarLogin.setOnClickListener { login() }
    }

    private fun observeState() {
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            when (state) {
                LoginUiState.Expired -> {
                    enableLogin(true)
                    showSpinner(false)
                    binding.txtErroLogin.setText(R.string.session_expired)
                    binding.txtErroLogin.visibility = View.VISIBLE
                }

                LoginUiState.Idle -> {
                    enableLogin(true)
                    showSpinner(false)
                }

                LoginUiState.Loading -> {
                    enableLogin(false)
                    showSpinner(true)
                    hideError()
                }

                LoginUiState.Authenticated -> {
                    // MainActivity releases navigation only after the shared profile gate succeeds.
                    enableLogin(false)
                    showSpinner(true)
                }

                is LoginUiState.Error -> {
                    enableLogin(!state.retryBlocked)
                    showSpinner(false)
                    notifyError(state.reason)
                }
            }
        }
    }

    fun enableLogin(enabled: Boolean) {
        binding.btEntrarLogin.isEnabled = enabled
    }

    fun showSpinner(show: Boolean) {
        binding.loadingLogin.visibility = if (show) View.VISIBLE else View.GONE
    }

    fun redirectToMainPage() {
        if (findNavController().currentDestination?.id == R.id.loginFragment) {
            findNavController().navigate(NavGraph.Actions.LOGIN_TO_HOME)
        }
    }

    fun notifyError(error: LoginError) {
        val message = when (error) {
            LoginError.NetworkError -> R.string.erro_login_internet
            LoginError.UserDisabled -> R.string.erro_login_usuario_desabilitado
            LoginError.InvalidCredentials -> R.string.erro_login_credenciais_invalidas
            LoginError.RateLimited -> R.string.session_rate_limit
            LoginError.PlatformNotAllowed -> R.string.login_platform_not_allowed
            LoginError.UnexpectedError -> R.string.erro_login_erro_inesperado
        }

        hideError()
        com.quistock.quistock.presentation.common.PopupFeedback.showStatus(
            binding.root,
            getString(message),
            isError = true,
        )
    }

    fun hideError() {
        binding.txtErroLogin.visibility = View.GONE
    }
}
