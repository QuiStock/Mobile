package com.quistock.secret_processor

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSVisitorVoid
import com.google.devtools.ksp.symbol.Modifier

class SecretValueVisitor(
    private val secretValue: KSClassDeclaration,
    private val logger: KSPLogger,
) : KSVisitorVoid(enableNewFeatures = true) {
    private val usedKeys = mutableMapOf<String, KSClassDeclaration>()

    override fun visitFile(file: KSFile, data: Unit) {
        file.declarations.forEach {
            it.accept(this, Unit)
        }
    }

    override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
        validate(classDeclaration)

        classDeclaration.declarations.forEach { it.accept(this, Unit) }
    }

    private fun validate(declaration: KSClassDeclaration) {
        if (Modifier.ABSTRACT in declaration.modifiers) return
        if (!declaration.isSubclassOf(secretValue)) return

        val annotation = declaration.findSecretKey() ?: run {
            logger.error(
                message = "${declaration.qualifiedName?.asString()} must declare @SecretKey",
                symbol = declaration,
            )
            return
        }

        val key = annotation.arguments
            .first { it.name?.asString() == "value" }
            .value as String

        if (key.isBlank()) {
            logger.error(
                message = "@SecreyKey value must not be blank",
                symbol = declaration,
            )
            return
        }

        usedKeys.putIfAbsent(key, declaration)?.let {
            logger.error(
                message = "Duplicate @SecretKey \"$key\". Already used by ${it.qualifiedName?.asString()}",
                symbol = declaration,
            )
        }
    }
}