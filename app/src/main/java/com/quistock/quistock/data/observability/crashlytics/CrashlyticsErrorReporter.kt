package com.quistock.quistock.data.observability.crashlytics

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.crashlytics.recordException
import com.quistock.quistock.domain.port.ErrorReporter

class CrashlyticsErrorReporter(val crashlytics: FirebaseCrashlytics) : ErrorReporter {
    override fun record(exception: Throwable, context: Map<String, String>) = crashlytics.recordException(exception) {
        context.forEach { (name, value) -> key(name, value) }
    }
}
