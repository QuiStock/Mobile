package com.quistock.quistock.presentation.interferences

import android.view.LayoutInflater
import android.view.ViewGroup
import com.quistock.quistock.R
import com.quistock.quistock.databinding.FragmentManagerPromotionDetailBinding
import com.quistock.quistock.domain.model.ActionType
import com.quistock.quistock.domain.model.InterferenceAction

class ManagerPromotionDetailFragment : ManagerRequestDetailFragment<FragmentManagerPromotionDetailBinding>() {
    override val type = ActionType.PROMOTION
    override val approvedRoute = R.id.action_managerPromotionDetailFragment_to_managerPromoApprovedFragment

    override fun inflate(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentManagerPromotionDetailBinding.inflate(inflater, container, false)

    override fun views(binding: FragmentManagerPromotionDetailBinding) = DetailViews(
        name = binding.tvNomeProdutoPromocao,
        flow = binding.tagFluxoBaixoPromocao,
        category = binding.tvCategoriaProdutoPromocao,
        expiry = binding.tvDataValidadePromocao,
        expiryHint = binding.tvDiasParaVencerPromocao,
        status = binding.tvStatusDecisaoPromocao,
        reason = binding.tvMotivoPromocao,
        decisionCard = binding.cardDecisaoPromocao,
        decisionLabel = binding.tvRotuloJustificativaPromocao,
        decisionText = binding.tvJustificativaDecisaoPromocao,
        approve = binding.btnAprovarPromocao,
        reject = binding.btnReprovarPromocao,
        back = binding.btnVoltarPromocao,
    )

    override fun bindValues(
        binding: FragmentManagerPromotionDetailBinding,
        action: InterferenceAction,
        text: RequestText,
    ) {
        binding.tvDescontoSugerido.text = text.discount(action)
        binding.tvDuracaoPromocao.text = text.duration(action)
    }
}
