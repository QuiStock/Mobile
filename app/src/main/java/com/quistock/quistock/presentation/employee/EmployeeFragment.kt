package com.quistock.quistock.presentation.employee

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.quistock.quistock.R
import com.quistock.quistock.databinding.FragmentEmployeeBinding
import com.quistock.quistock.domain.model.TeamResult
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class EmployeeFragment : Fragment() {
    private val model: TeamViewModel by viewModel()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_employee, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentEmployeeBinding.bind(view)
        binding.employeeList.removeAllViews()
        binding.btnCadastrarFuncionario.setOnClickListener {
            findNavController().navigate(R.id.action_employeeFragment_to_employeeDetailFragment)
        }
        binding.etBuscaFuncionario.doAfterTextChanged {
            renderMembers(binding)
        }
        bindSortControl(binding)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { model.sort.collect { sort -> renderSort(binding, sort) } }
                model.state.collect { state ->
                    when {
                        state.busy -> showMessage(binding.employeeList, "Carregando funcionários…")

                        state.result is TeamResult.Success -> renderMembers(binding)

                        state.result != null -> showMessage(
                            binding.employeeList,
                            "Não foi possível carregar. Toque para tentar novamente.",
                        )
                    }
                }
            }
        }
    }

    private fun bindSortControl(binding: FragmentEmployeeBinding) {
        binding.dropdownFiltroDataFuncionario.setOnClickListener { anchor ->
            val popup = PopupMenu(requireContext(), anchor)
            TeamSort.entries.forEach { popup.menu.add(Menu.NONE, it.ordinal, it.ordinal, it.label()) }
            popup.setOnMenuItemClickListener { item ->
                model.setSort(TeamSort.entries[item.itemId])
                true
            }
            popup.show()
        }
        binding.btnLimparFiltroFuncionario.setOnClickListener { model.setSort(null) }
    }

    private fun renderSort(binding: FragmentEmployeeBinding, sort: TeamSort?) {
        binding.tvFiltroDataFuncionario.setText(sort?.label() ?: R.string.employee_sort_placeholder)
        binding.ivSetaFiltroFuncionario.isVisible = sort == null
        binding.btnLimparFiltroFuncionario.isVisible = sort != null
        renderMembers(binding)
    }

    override fun onResume() {
        super.onResume()
        model.load()
    }

    private fun renderMembers(binding: FragmentEmployeeBinding) {
        val result = model.state.value.result as? TeamResult.Success ?: return
        val list = binding.employeeList
        list.removeAllViews()
        val visible = visibleMembers(result.members, binding.etBuscaFuncionario.text.toString(), model.sort.value)
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
            (card.layoutParams as? LinearLayout.LayoutParams)?.bottomMargin =
                (CARD_SPACING_DP * resources.displayMetrics.density).toInt()
            list.addView(card)
        }
    }

    private fun showMessage(list: LinearLayout, message: String, retry: Boolean = true) {
        list.removeAllViews()
        list.addView(
            TextView(requireContext()).apply {
                text = message
                setTextColor(context.getColor(R.color.color_on_surface))
                if (retry && !model.state.value.busy) setOnClickListener { model.load() }
            },
        )
    }

    @StringRes
    private fun TeamSort.label(): Int = when (this) {
        TeamSort.NEWEST -> R.string.employee_sort_newest
        TeamSort.OLDEST -> R.string.employee_sort_oldest
        TeamSort.ALPHABETICAL -> R.string.employee_sort_alphabetical
    }

    private companion object {
        const val CARD_SPACING_DP = 16
    }
}
