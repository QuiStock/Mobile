package com.quistock.quistock.domain.cache

import kotlin.time.Instant

data class CachedValue<T>(val value: T, val savedAt: Instant, val expiresAt: Instant?)
