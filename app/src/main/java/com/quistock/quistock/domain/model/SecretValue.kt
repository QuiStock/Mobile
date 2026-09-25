package com.quistock.quistock.domain.model

import kotlin.time.Instant

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class SecretKey(val value: String)

abstract class SecretValue(open val expiresAt: Instant?)
