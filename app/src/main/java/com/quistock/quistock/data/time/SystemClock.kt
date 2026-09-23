package com.quistock.quistock.data.time

import com.quistock.quistock.domain.time.Clock
import kotlinx.datetime.TimeZone

class SystemClock :
    Clock(
        clock = kotlin.time.Clock.System,
        timeZone = TimeZone.currentSystemDefault(),
    )
