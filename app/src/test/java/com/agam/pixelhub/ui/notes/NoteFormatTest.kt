package com.agam.pixelhub.ui.notes

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.agam.pixelhub.ui.notes.NoteFormat.Block
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoteFormatTest {
    private fun v(text: String, s: Int, e: Int = s) = TextFieldValue(text, TextRange(s, e))

    @Test fun wrapsAndUnwrapsTheSelection() {
        val bold = NoteFormat.wrap(v("buy milk", 4, 8), "**")
        assertEquals("buy **milk**", bold.text)
        assertEquals(TextRange(6, 10), bold.selection)
        assertEquals("buy milk", NoteFormat.wrap(bold, "**").text)
        // The markers selected too.
        assertEquals("buy milk", NoteFormat.wrap(v("buy **milk**", 4, 12), "**").text)
    }

    @Test fun emptySelectionDropsAPair() {
        val r = NoteFormat.wrap(v("a ", 2), "_")
        assertEquals("a __", r.text)
        assertEquals(TextRange(3), r.selection)
    }

    @Test fun blockSwapsRatherThanStacks() {
        val h = NoteFormat.toggleBlock(v("Plan", 2), Block.H1)
        assertEquals("# Plan", h.text)
        assertEquals(TextRange(4), h.selection)
        assertEquals("## Plan", NoteFormat.toggleBlock(h, Block.H2).text)
        assertEquals("Plan", NoteFormat.toggleBlock(h, Block.H1).text)
    }

    @Test fun blockCoversEverySelectedLine() {
        val r = NoteFormat.toggleBlock(v("eggs\nmilk\nbread", 0, 9), Block.CHECK)
        assertEquals("- [ ] eggs\n- [ ] milk\nbread", r.text)
        // A ticked line counts as a checklist line, so this takes them all off.
        assertEquals("eggs\nmilk", NoteFormat.toggleBlock(v("- [x] eggs\n- [ ] milk", 0, 20), Block.CHECK).text)
    }

    @Test fun enterContinuesAndEndsLists() {
        val old = v("- [x] eggs", 10)
        val next = NoteFormat.continueList(old, v("- [x] eggs\n", 11))
        assertEquals("- [x] eggs\n- [ ] ", next.text)
        assertEquals(TextRange(17), next.selection)
        val ended = NoteFormat.continueList(next, v("- [x] eggs\n- [ ] \n", 18))
        assertEquals("- [x] eggs\n", ended.text)
        // Plain lines are left alone.
        val plain = v("hi\n", 3)
        assertEquals(plain, NoteFormat.continueList(v("hi", 2), plain))
    }

    @Test fun tapOnTheBoxTicksIt() {
        assertEquals("a\n- [x] eggs", NoteFormat.toggleCheckAt("a\n- [ ] eggs", 4))
        assertEquals("- [ ] eggs", NoteFormat.toggleCheckAt("- [x] eggs", 0))
        assertNull(NoteFormat.toggleCheckAt("- [ ] eggs", 8))
        assertNull(NoteFormat.toggleCheckAt("- eggs", 1))
    }

    @Test fun cardDropsMarkersEditorKeepsThem() {
        val p = NoteFormat.Palette(Color.Gray, Color.Blue)
        val text = "# Trip\n- [x] **tickets**\nsnake_case _yes_"
        assertEquals(text, NoteFormat.styled(text, p, forEditor = true).text)
        assertEquals("Trip\n☑  tickets\nsnake_case yes", NoteFormat.styled(text, p, forEditor = false).text)
    }
}
