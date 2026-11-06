package com.agam.pixelhub.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotesTest {
    @Test fun blankAddsNothing() {
        assertNull(NotesRepository.clean(""))
        assertNull(NotesRepository.clean("   \n "))
    }

    @Test fun trimsAroundTheText() = assertEquals("buy milk", NotesRepository.clean("  buy milk \n"))

    @Test fun capsVeryLongPastes() {
        val long = "a".repeat(NotesRepository.MAX_LENGTH + 500)
        assertEquals(NotesRepository.MAX_LENGTH, NotesRepository.clean(long)!!.length)
    }
}
