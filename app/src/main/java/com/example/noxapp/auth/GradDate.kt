package com.example.noxapp.auth

import java.util.Calendar
import java.util.Date
import java.util.TimeZone

// converts grad date --> account termination date
object GradDate {
    private val campusTimeZone: TimeZone = TimeZone.getTimeZone("America/Toronto")
    //function which decides when account is experienced. If grad date in april 2027, account expires 00:00 May 1, 2027
    fun accessExpiresAt(gradMonth: Int, gradYear: Int): Date {
        val calendar = Calendar.getInstance(campusTimeZone).apply {
            clear()                              // zero out hours/minutes/seconds
            set(gradYear, gradMonth - 1, 1) // -1 is our selection will use 1-12 for months, but calendar uses 0-11
            add(Calendar.MONTH, 1)               // handles rollover
        }
        return calendar.time
    }

}