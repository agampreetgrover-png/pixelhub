package com.agam.pixelhub.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarWidgetTest {
    @Test fun countdownIsSpokenWithRealPlurals() {
        assertEquals("in 1 hour", spoken("in 1h"))
        assertEquals("in 2 hours 15 minutes", spoken("in 2h 15m"))
        assertEquals("in 1 minute", spoken("in 1m"))
        assertEquals("in 3 days", spoken("in 3d"))
        assertEquals("now", spoken("now"))
    }
}
