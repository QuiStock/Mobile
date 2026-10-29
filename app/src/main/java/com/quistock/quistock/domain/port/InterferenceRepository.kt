package com.quistock.quistock.domain.port

import com.quistock.quistock.domain.model.ActionDraft
import com.quistock.quistock.domain.model.ActionListResult
import com.quistock.quistock.domain.model.ActionResult
import com.quistock.quistock.domain.model.ActionType
import com.quistock.quistock.domain.model.AlertProduct
import com.quistock.quistock.domain.model.InterferenceAction
import com.quistock.quistock.domain.model.SessionSnapshot

interface InterferenceRepository {
    suspend fun list(storeId: Long, type: ActionType?, expected: SessionSnapshot): ActionListResult
    suspend fun generate(
        product: AlertProduct,
        storeId: Long,
        type: ActionType,
        draft: ActionDraft,
        expected: SessionSnapshot,
    ): ActionResult
    suspend fun forward(action: InterferenceAction, expected: SessionSnapshot): ActionResult
}
