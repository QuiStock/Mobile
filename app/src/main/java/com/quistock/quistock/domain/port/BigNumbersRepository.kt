package com.quistock.quistock.domain.port

import com.quistock.quistock.domain.cache.CacheCoordinator
import com.quistock.quistock.domain.cache.LocalSource
import com.quistock.quistock.domain.cache.RemoteSource
import com.quistock.quistock.domain.model.BigNumbers
import com.quistock.quistock.domain.time.Clock
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration

interface CachedBigNumbersRepository : LocalSource<BigNumbers>

interface RemoteBigNumbersRepository : RemoteSource<BigNumbers>

class BigNumbersRepository(
    remote: RemoteBigNumbersRepository,
    local: CachedBigNumbersRepository,
    clock: Clock,
    logger: Logger,
) : CacheCoordinator<BigNumbers>(
    remote = remote,
    local = local,
    clock = clock,
    logger = logger,
)
