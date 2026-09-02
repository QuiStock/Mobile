package com.quistock.quistock.presentation.login

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.quistock.quistock.R
import com.quistock.quistock.databinding.FragmentLoginBinding
import com.quistock.quistock.domain.model.LoginError
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
        observeState()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun login() {
        val email = binding.emailLogin.text.toString()
        val password = binding.senhaLogin.text.toString()
        if (email.isBlank() || password.isBlank()) return

        viewModel.authenticate(email = email, password = password)
    }

    fun setupListeners() {
        binding.btEntrarLogin.setOnClickListener { login() }
    }

    fun observeState() {
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            when (state) {
                LoginUiState.Idle -> enableLogin(true)

                LoginUiState.Loading -> {
                    enableLogin(false)
                    // TODO: set loading text
                }

                LoginUiState.Authenticated -> {
                    enableLogin(true)
                    redirectToMainPage()
                }

                is LoginUiState.Error -> {
                    enableLogin(true)
                    notifyError(state.reason)
                }
            }
        }
    }

    fun enableLogin(enabled: Boolean) {
        binding.btEntrarLogin.isEnabled = enabled
    }

    fun redirectToMainPage() {
        // TODO: redirect to main page
        Toast.makeText(
            context,
            "Login funcionou!",
            Toast.LENGTH_LONG,
        ).show()
    }

    fun notifyError(error: LoginError) {
        val message = when (error) {
            LoginError.NetworkError -> R.string.erro_login_internet
            LoginError.UserDisabled -> R.string.erro_login_usuario_desabilitado
            LoginError.InvalidCredentials -> R.string.erro_login_credenciais_invalidas
            LoginError.UnexpectedError -> R.string.erro_login_erro_inesperado
        }

        // TODO: set text view to the error message
        Toast.makeText(
            context,
            message,
            Toast.LENGTH_LONG,
        ).show()
    }
}
