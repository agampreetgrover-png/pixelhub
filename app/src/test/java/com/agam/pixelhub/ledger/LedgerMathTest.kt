package com.agam.pixelhub.ledger

import com.agam.pixelhub.data.LedgerRecord
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class LedgerMathTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun on(day: Int) = LocalDate.of(2026, 11, day).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    private fun r(id: Long, rupees: Long, day: Int, note: String = "") = LedgerRecord(id, 1, rupees * 100, note, on(day))

    @Test fun balanceTexts() {
        assertEquals("Neha owes you ₹230", LedgerMath.balanceText("Neha", 23_000))
        assertEquals("You owe Neha ₹50", LedgerMath.balanceText("Neha", -5_000))
        assertEquals("All square with Neha", LedgerMath.balanceText("Neha", 0))
    }

    @Test fun shortTexts() {
        assertEquals("owes you ₹230", LedgerMath.shortText(23_000))
        assertEquals("you owe ₹50", LedgerMath.shortText(-5_000))
        assertEquals("All square", LedgerMath.shortText(0))
    }

    @Test fun tidiesNames() = assertEquals("Neha Sharma", LedgerMath.tidyName("  neha   sharma "))

    @Test fun runningBalancesNewestFirst() {
        // Oldest to newest: +100, -30, +50 → balances 100, 70, 120.
        val newestFirst = listOf(r(3, 50, 3), r(2, -30, 2), r(1, 100, 1))
        assertEquals(listOf(12_000L, 7_000L, 10_000L), LedgerMath.runningBalances(newestFirst))
    }

    @Test fun openEntriesStartAfterLastZero() {
        // +100, -100 (square), +150, +80 → open = the last two, oldest first.
        val newestFirst = listOf(r(4, 80, 5, "Auto"), r(3, 150, 3, "Lunch"), r(2, -100, 2), r(1, 100, 1))
        assertEquals(listOf(3L, 4L), LedgerMath.openEntries(newestFirst).map { it.id })
    }

    @Test fun openEntriesWhenNeverSquare() {
        val newestFirst = listOf(r(2, -30, 2), r(1, 100, 1))
        assertEquals(listOf(1L, 2L), LedgerMath.openEntries(newestFirst).map { it.id })
    }

    @Test fun shareMessageWhenTheyOweYou() {
        val open = listOf(r(3, 150, 3, "Lunch"), r(4, 80, 5, "Auto"))
        assertEquals(
            "Hi Neha, quick reminder: you owe me ₹230.\n• 3 Nov · Lunch · ₹150\n• 5 Nov · Auto · ₹80",
            LedgerMath.shareMessage("Neha Sharma", 23_000, open, zone),
        )
    }

    @Test fun shareMessageMarksTheirPayments() {
        val open = listOf(r(1, 100, 1), r(2, -30, 2))
        assertEquals(
            "Hi Neha, quick reminder: you owe me ₹70.\n• 1 Nov · I paid · ₹100\n• 2 Nov · you paid · −₹30",
            LedgerMath.shareMessage("Neha", 7_000, open, zone),
        )
    }

    @Test fun shareMessageWhenYouOweThem() =
        assertEquals("Hi Neha, I owe you ₹50. Will settle soon.", LedgerMath.shareMessage("Neha", -5_000, emptyList(), zone))
}
