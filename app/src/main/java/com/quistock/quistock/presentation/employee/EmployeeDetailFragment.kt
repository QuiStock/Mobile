package com.quistock.quistock.presentation.employee

import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.quistock.quistock.R
import com.quistock.quistock.databinding.FragmentEmployeeDetailBinding
import com.quistock.quistock.domain.model.TeamResult
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class EmployeeDetailFragment : Fragment(R.layout.fragment_employee_detail) {
    private val model: TeamViewModel by viewModel()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentEmployeeDetailBinding.bind(view)
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (!model.state.value.busy) findNavController().popBackStack()
                }
            },
        )
        binding.btnVoltarCadastro.setOnClickListener { if (!model.state.value.busy) findNavController().popBackStack() }
        binding.btnCancelar.setOnClickListener { if (!model.state.value.busy) findNavController().popBackStack() }
        binding.btnCadastrar.setOnClickListener { submit(binding) }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.state.collect { state ->
                    binding.btnCadastrar.isEnabled = !state.busy && state.result != TeamResult.Uncertain
                    renderResult(binding, state.result)
                }
            }
        }
    }

    private fun submit(binding: FragmentEmployeeDetailBinding) {
        val name = binding.etNomeCompleto.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        when {
            name.isBlank() || name.length > MAX_NAME_LENGTH -> binding.etNomeCompleto.error = "Informe um nome válido."

            email.length > MAX_EMAIL_LENGTH || !Patterns.EMAIL_ADDRESS.matcher(email).matches() ->
                binding.etEmail.error = "Informe um e-mail válido."

            else -> model.create(name, email)
        }
    }

    private fun renderResult(binding: FragmentEmployeeDetailBinding, result: TeamResult?) {
        when (result) {
            is TeamResult.Success -> {
                Toast.makeText(requireContext(), "Funcionário cadastrado.", Toast.LENGTH_SHORT).show()
                if (findNavController().currentDestination?.id == R.id.employeeDetailFragment) {
                    findNavController().popBackStack()
                }
            }

            TeamResult.Conflict -> binding.etEmail.error = "Este e-mail já está cadastrado."

            TeamResult.Forbidden -> message("Você não tem permissão para cadastrar funcionários.")

            TeamResult.NotConfigured -> message("Senha inicial não configurada no aplicativo.")

            TeamResult.Failure -> message("Não foi possível cadastrar. Verifique os dados e tente novamente.")

            TeamResult.Uncertain -> message(
                "Não foi possível confirmar o cadastro. Confira a lista antes de tentar novamente.",
            )

            null -> Unit
        }
    }

    private fun message(text: String) = Toast.makeText(requireContext(), text, Toast.LENGTH_LONG).show()

    private companion object {
        const val MAX_NAME_LENGTH = 150
        const val MAX_EMAIL_LENGTH = 255
    }
}
