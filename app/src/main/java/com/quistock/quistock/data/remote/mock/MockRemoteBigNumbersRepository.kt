package com.quistock.quistock.data.remote.mock

import com.quistock.quistock.domain.model.BigNumbers
import com.quistock.quistock.domain.port.RemoteBigNumbersRepository
import okio.IOException
import kotlin.time.Clock

/** Sample remote data for exercising the big numbers UI before the API is available. */
class MockRemoteBigNumbersRepository : RemoteBigNumbersRepository {
    var numbers: BigNumbers =
        BigNumbers(
            nearExpirationProductCount = 12,
            criticalAnalyzedFlowCount = 3,
            activeActionCount = 7,
            createdAt = Clock.System.now(),
        )
    var failure: IOException? = null

    override suspend fun fetch(): BigNumbers {
        failure?.let { throw it }
        return numbers.copy(createdAt = Clock.System.now())
    }
}
