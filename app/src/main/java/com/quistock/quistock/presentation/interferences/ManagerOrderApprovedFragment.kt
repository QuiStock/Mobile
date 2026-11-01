package com.quistock.quistock.presentation.interferences

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.quistock.quistock.R
import com.quistock.quistock.databinding.FragmentManagerOrderApprovedBinding
import com.quistock.quistock.domain.model.ActionStatus
import org.koin.androidx.viewmodel.ext.android.activityViewModel

/** Confirms a recorded approval only; it never claims ERP execution or delivery. */
class ManagerOrderApprovedFragment : Fragment() {
    private val model: ManagerInterferencesViewModel by activityViewModel()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        FragmentManagerOrderApprovedBinding.inflate(inflater, container, false).also { views ->
            val action = model.state.value.selected?.takeIf { it.status == ActionStatus.APPROVED }
            val text = RequestText(inflater.context)
            views.tvSubtituloPedidoAprovado.setText(R.string.manager_approved_subtitle)
            if (action != null) {
                views.tvResumoPedido.text = text.title(action, model.state.value.product(action))
                views.tvResumoQuantidade.text = text.quantity(action)
                views.tvResumoPrevisao.text = text.desiredDate(action)
            }
            views.btnVoltarInterferencias.setOnClickListener {
                findNavController().navigate(R.id.action_managerOrderApprovedFragment_to_managerInterferencesFragment)
            }
        }.root
}
