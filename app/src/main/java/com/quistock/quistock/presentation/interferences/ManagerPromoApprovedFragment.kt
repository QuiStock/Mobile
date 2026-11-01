package com.quistock.quistock.presentation.interferences

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.quistock.quistock.R
import com.quistock.quistock.databinding.FragmentManagerPromoApprovedBinding
import com.quistock.quistock.domain.model.ActionStatus
import org.koin.androidx.viewmodel.ext.android.activityViewModel

/** Confirms a recorded approval only; the promotion is not reported as active in the ERP. */
class ManagerPromoApprovedFragment : Fragment() {
    private val model: ManagerInterferencesViewModel by activityViewModel()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        FragmentManagerPromoApprovedBinding.inflate(inflater, container, false).also { views ->
            val action = model.state.value.selected?.takeIf { it.status == ActionStatus.APPROVED }
            val text = RequestText(inflater.context)
            views.tvSubtituloPromocaoAprovada.setText(R.string.manager_approved_subtitle)
            views.tvResumoStatus.setText(R.string.manager_approved_status)
            if (action != null) {
                views.tvResumoProduto.text = text.title(action, model.state.value.product(action))
                views.tvResumoPeriodo.text = text.period(action)
            }
            views.btnVoltarInterferencias.setOnClickListener {
                findNavController().navigate(R.id.action_managerPromoApprovedFragment_to_managerInterferencesFragment)
            }
        }.root
}
