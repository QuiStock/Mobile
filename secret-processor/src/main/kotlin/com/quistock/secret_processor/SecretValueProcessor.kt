package com.quistock.secret_processor

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated

const val SECRET_VALUE_FQN = "com.quistock.quistock.domain.model.SecretValue"
const val SECRET_KEY_FQN = "com.quistock.quistock.domain.model.SecretKey"

class SecretValueProcessor(private val logger: KSPLogger) : SymbolProcessor {
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val secretValue = resolver.getClassDeclarationByName(
            resolver.getKSNameFromString(SECRET_VALUE_FQN)
        ) ?: return emptyList()

        val visitor = SecretValueVisitor(
            secretValue = secretValue,
            logger = logger,
        )

        resolver.getNewFiles().forEach { it.accept(visitor, Unit) }

        return emptyList()
    }
}