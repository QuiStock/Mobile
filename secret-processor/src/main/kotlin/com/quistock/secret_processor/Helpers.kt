package com.quistock.secret_processor

import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration

fun KSClassDeclaration.isSubclassOf(target: KSClassDeclaration): Boolean = superTypes.any { superTypeReference ->
    val superDeclaration = superTypeReference.resolve().declaration as? KSClassDeclaration
        ?: return@any false

    superDeclaration == target || superDeclaration.isSubclassOf(target)
}

fun KSClassDeclaration.findSecretKey(): KSAnnotation? = annotations.firstOrNull {
    it.annotationType
        .resolve()
        .declaration
        .qualifiedName
        ?.asString() == SECRET_KEY_FQN
}
