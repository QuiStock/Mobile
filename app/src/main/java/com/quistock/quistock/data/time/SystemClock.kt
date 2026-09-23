package com.quistock.quistock.data.time

import com.quistock.quistock.domain.port.Clock
import kotlinx.datetime.TimeZone

class SystemClock :
    Clock(
        clock = kotlin.time.Clock.System,
        timeZone = TimeZone.currentSystemDefault(),
    )
