package com.quistock.quistock.data.remote.internal.mapper

import com.quistock.quistock.data.remote.internal.dto.FlowTypeDTO
import com.quistock.quistock.data.remote.internal.dto.SuggestedActionDTO
import com.quistock.quistock.domain.model.FlowType
import com.quistock.quistock.domain.model.SuggestedAction
import io.kotest.matchers.shouldBe
import org.junit.Test

class ChatbotEnumMappersTests {
    @Test
    fun `flow types should map every API value to its domain value`() {
        FlowTypeDTO.HIGH.toDomain() shouldBe FlowType.HIGH
        FlowTypeDTO.MEDIUM.toDomain() shouldBe FlowType.MEDIUM
        FlowTypeDTO.LOW.toDomain() shouldBe FlowType.LOW
    }

    @Test
    fun `suggested actions should map every API value to its domain value`() {
        SuggestedActionDTO.PROMOTION.toDomain() shouldBe SuggestedAction.PROMOTION
        SuggestedActionDTO.MONITOR.toDomain() shouldBe SuggestedAction.MONITOR
        SuggestedActionDTO.STOCK_ORDER.toDomain() shouldBe SuggestedAction.STOCK_ORDER
    }
}
