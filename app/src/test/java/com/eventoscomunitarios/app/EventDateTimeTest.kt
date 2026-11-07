package com.eventoscomunitarios.app

import com.eventoscomunitarios.app.core.util.eventFormDate
import com.eventoscomunitarios.app.core.util.eventFormTime
import com.eventoscomunitarios.app.core.util.parseEventDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class EventDateTimeTest {
    @Test fun impossibleCalendarDatesAndTimesAreRejectedWithoutRollingForward() {
        assertNull(parseEventDateTime("31/02/2027", "10:00"))
        assertNull(parseEventDateTime("29/02/2027", "10:00"))
        assertNull(parseEventDateTime("31/04/2027", "10:00"))
        assertNull(parseEventDateTime("01/12/2027", "24:00"))
        assertNull(parseEventDateTime("01/12/2027", "12:60"))
    }

    @Test fun onlyTheCompletePickerFormatIsAccepted() {
        assertNull(parseEventDateTime("Sáb 27 sep", "3:00 p.m."))
        assertNull(parseEventDateTime("1/12/2027", "09:00"))
        assertNull(parseEventDateTime("01/12/2027", "9:00"))
        assertNull(parseEventDateTime("01/12/2027 extra", "09:00"))
        assertNull(parseEventDateTime("01/12/2027", "09:00 extra"))
        assertNull(parseEventDateTime("", ""))
    }

    @Test fun validLeapDayKeepsTheDateAndTimeSelectedByTheUser() {
        val timestamp = parseEventDateTime("29/02/2028", "23:59")
        assertNotNull(timestamp)
        assertEquals("29/02/2028", eventFormDate(timestamp!!))
        assertEquals("23:59", eventFormTime(timestamp))
    }
}
