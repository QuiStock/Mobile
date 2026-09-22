package com.quistock.quistock.domain.port

import com.quistock.quistock.domain.model.BigNumbers
import kotlinx.coroutines.flow.StateFlow

interface BigNumbersRepository : RefreshableRepository {
    val bigNumbers: StateFlow<BigNumbers>
}
