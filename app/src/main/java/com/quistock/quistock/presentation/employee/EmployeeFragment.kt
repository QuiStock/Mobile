package com.quistock.quistock.presentation.employee

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.quistock.quistock.R
import com.quistock.quistock.databinding.FragmentEmployeeBinding
import com.quistock.quistock.domain.model.TeamMember
import com.quistock.quistock.domain.model.TeamResult
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class EmployeeFragment : Fragment() {
    private val model: TeamViewModel by viewModel()
    private var members = emptyList<TeamMember>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_employee, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentEmployeeBinding.bind(view)
        binding.employeeList.removeAllViews()
        binding.btnCadastrarFuncionario.setOnClickListener {
            findNavController().navigate(R.id.action_employeeFragment_to_employeeDetailFragment)
        }
        binding.etBuscaFuncionario.doAfterTextChanged {
            if (model.state.value.result is TeamResult.Success) renderMembers(binding)
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.state.collect { state ->
                    when {
                        state.busy -> showMessage(binding.employeeList, "Carregando funcionários…")
                        state.result is TeamResult.Success -> {
                            members = state.result.members
                            renderMembers(binding)
                        }
                        state.result != null -> showMessage(binding.employeeList, "Não foi possível carregar. Toque para tentar novamente.")
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        model.load()
    }

    private fun renderMembers(binding: FragmentEmployeeBinding) {
        val list = binding.employeeList
        list.removeAllViews()
        val query = binding.etBuscaFuncionario.text.toString().trim()
        val visible = members.filter { it.name.contains(query, ignoreCase = true) }
        if (visible.isEmpty()) {
            showMessage(list, "Nenhum funcionário encontrado.", retry = false)
            return
        }
        visible.forEach { member ->
            val card = layoutInflater.inflate(R.layout.item_employee_card, list, false)
            card.findViewById<TextView>(R.id.tvNomeFuncionario).text = member.name
            card.findViewById<TextView>(R.id.tvCargoFuncionario).text = "Funcionário"
            card.findViewById<TextView>(R.id.tvDataInicioFuncionario).text = when (member.status) {
                "ACTIVE" -> "Ativo"
                "INACTIVE" -> "Inativo"
                else -> "Status indisponível"
            }
            (card.layoutParams as? LinearLayout.LayoutParams)?.bottomMargin = (16 * resources.displayMetrics.density).toInt()
            list.addView(card)
        }
    }

    private fun showMessage(list: LinearLayout, message: String, retry: Boolean = true) {
        list.removeAllViews()
        list.addView(TextView(requireContext()).apply {
            text = message
            setTextColor(android.graphics.Color.WHITE)
            if (retry && !model.state.value.busy) setOnClickListener { model.load() }
        })
    }
}
