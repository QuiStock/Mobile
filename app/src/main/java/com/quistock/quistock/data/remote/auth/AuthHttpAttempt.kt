package com.quistock.quistock.data.remote.auth

import java.io.IOException

/** A logical Auth call may reach the network once, including HTTP-level follow-ups. */
internal class AuthHttpAttempt {
    private var attempted = false
    var status: Int = 0

    @Synchronized
    fun claim() {
        if (attempted) throw AuthFollowUpBlocked(status)
        attempted = true
    }
}

internal class AuthFollowUpBlocked(val status: Int) : IOException("Auth HTTP follow-up blocked")
