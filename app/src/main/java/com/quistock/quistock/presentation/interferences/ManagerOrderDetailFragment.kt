package com.quistock.quistock.presentation.interferences

import android.view.LayoutInflater
import android.view.ViewGroup
import com.quistock.quistock.R
import com.quistock.quistock.databinding.FragmentManagerOrderDetailBinding
import com.quistock.quistock.domain.model.ActionType
import com.quistock.quistock.domain.model.InterferenceAction

class ManagerOrderDetailFragment : ManagerRequestDetailFragment<FragmentManagerOrderDetailBinding>() {
    override val type = ActionType.ORDER
    override val approvedRoute = R.id.action_managerOrderDetailFragment_to_managerOrderApprovedFragment

    override fun inflate(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentManagerOrderDetailBinding.inflate(inflater, container, false)

    override fun views(binding: FragmentManagerOrderDetailBinding) = DetailViews(
        name = binding.tvNomeProdutoPedido,
        flow = binding.tagFluxoBaixoPedido,
        category = binding.tvCategoriaProdutoPedido,
        expiry = binding.tvDataValidadePedido,
        expiryHint = binding.tvDiasParaVencerPedido,
        status = binding.tvStatusDecisaoPedido,
        reason = binding.tvMotivoPedido,
        decisionCard = binding.cardDecisaoPedido,
        decisionLabel = binding.tvRotuloJustificativaPedido,
        decisionText = binding.tvJustificativaDecisaoPedido,
        approve = binding.btnAprovarPedido,
        reject = binding.btnReprovarPedido,
        back = binding.btnVoltarPedido,
    )

    override fun bindValues(binding: FragmentManagerOrderDetailBinding, action: InterferenceAction, text: RequestText) {
        binding.tvQuantidadeSugerida.text = text.quantity(action)
        binding.tvDataDesejada.text = text.desiredDate(action)
    }
}
