package com.quistock.quistock.data.remote.internal.mapper

import com.quistock.quistock.data.remote.internal.dto.FlowTypeDTO
import com.quistock.quistock.domain.model.FlowType

fun FlowTypeDTO.toDomain(): FlowType = when (this) {
    FlowTypeDTO.HIGH -> FlowType.HIGH
    FlowTypeDTO.MEDIUM -> FlowType.MEDIUM
    FlowTypeDTO.LOW -> FlowType.LOW
}
