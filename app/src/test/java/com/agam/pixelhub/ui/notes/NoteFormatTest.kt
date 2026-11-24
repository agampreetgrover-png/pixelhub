package com.agam.pixelhub.ui.notes

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import com.agam.pixelhub.ui.notes.NoteFormat.Block
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test fun italicTakesInTheWholeWord() {
        val r = NoteFormat.wrap(v("foobar", 3, 6), "_")
        assertEquals("_foobar_", r.text)
        assertEquals(TextRange(1, 7), r.selection)
        assertEquals("foobar", NoteFormat.wrap(r, "_").text)
        assertEquals("foobar", NoteFormat.styled(r.text, NoteFormat.Palette(Color.Gray, Color.Blue), forEditor = false).text)
    }

    @Test fun wrapOverLinesWrapsEachLine() {
        val r = NoteFormat.wrap(v("eggs\nmilk", 0, 9), "**")
        assertEquals("**eggs**\n**milk**", r.text)
        assertEquals("eggs\nmilk", NoteFormat.wrap(r, "**").text)
        // List marks stay outside, so the lines are still a list.
        assertEquals("- **eggs**\n- **milk**", NoteFormat.wrap(v("- eggs\n- milk", 0, 13), "**").text)
    }

    @Test fun checklistKeepsTicksAndSkipsBlankLines() {
        assertEquals("- [x] eggs\n- [ ] bread", NoteFormat.toggleBlock(v("- [x] eggs\nbread", 0, 16), Block.CHECK).text)
        assertEquals("eggs\nmilk\n", NoteFormat.toggleBlock(v("- [x] eggs\n- [ ] milk\n", 0, 22), Block.CHECK).text)
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
        // Enter just after the mark splits the item instead of deleting the mark.
        val split = NoteFormat.continueList(v("- milk", 2), v("- \nmilk", 3))
        assertEquals("- \n- milk", split.text)
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
        // Formatting inside formatting.
        assertEquals("w", NoteFormat.styled("**_w_**", p, forEditor = false).text)
        assertEquals("**_w_**", NoteFormat.styled("**_w_**", p, forEditor = true).text)
    }

    @Test fun italicWithABareCursorKeepsTheWord() {
        // Right after a word: the word goes italic, and typing carries on inside it.
        val end = NoteFormat.wrap(v("Hello", 5), "_")
        assertEquals("_Hello_", end.text)
        assertEquals(TextRange(6), end.selection)
        // Inside a word, the cursor stays between the same letters.
        val mid = NoteFormat.wrap(v("foobar", 3), "_")
        assertEquals("_foobar_", mid.text)
        assertEquals(TextRange(4), mid.selection)
        val start = NoteFormat.wrap(v("foo bar", 4), "_")
        assertEquals("foo _bar_", start.text)
        assertTrue(start.selection.collapsed)
        // Tapping again inside the italic word takes it off, not nests another pair.
        val off = NoteFormat.wrap(mid, "_")
        assertEquals("foobar", off.text)
        assertEquals(TextRange(3), off.selection)
        assertEquals("foobar", NoteFormat.wrap(v("_foobar_", 2, 4), "_").text)
        assertEquals("Hello", NoteFormat.wrap(end, "_").text)
        // snake_case is still one word.
        assertEquals("_snake_case_", NoteFormat.wrap(v("snake_case", 2), "_").text)
    }

    @Test fun italicKnowsNonAsciiWords() {
        val p = NoteFormat.Palette(Color.Gray, Color.Blue)
        assertEquals("_café_", NoteFormat.wrap(v("café", 0, 3), "_").text)
        assertEquals("_écrit_", NoteFormat.wrap(v("écrit", 1, 5), "_").text)
        assertEquals("_नमस्ते_", NoteFormat.wrap(v("नमस्ते", 0, 2), "_").text)
        assertEquals("café x", NoteFormat.styled("_café_ x", p, forEditor = false).text)
        // A half-wrapped word stays plain, as it does on the phone, where \w is Unicode.
        assertEquals("_caf_é", NoteFormat.styled("_caf_é", p, forEditor = false).text)
    }

    @Test fun italicOffWorksForAPhrase() {
        // Typed into an empty pair, then tapped again just before the closing marker.
        val typed = NoteFormat.wrap(v("_hello world_", 12), "_")
        assertEquals("hello world", typed.text)
        assertEquals(TextRange(11), typed.selection)
        assertEquals("foo bar", NoteFormat.wrap(v("_foo bar_", 4), "_").text)
        assertEquals(TextRange(2, 5), NoteFormat.wrap(v("x _foo bar_", 3, 6), "_").selection)
        // Inside bold, and after a list mark.
        assertEquals("**a b**", NoteFormat.wrap(v("**_a b_**", 4), "_").text)
        assertEquals("- [ ] buy milk", NoteFormat.wrap(v("- [ ] _buy milk_", 10), "_").text)
        // Only the pair around the cursor comes off.
        assertEquals("_a_ b c", NoteFormat.wrap(v("_a_ _b c_", 6), "_").text)
        // Outside any italic it still wraps the word.
        assertEquals("_a_ _b_", NoteFormat.wrap(v("_a_ b", 5), "_").text)
    }

    @Test fun italicKnowsLettersOutsideTheBmp() {
        val ext = "𠀀" // U+20000, a CJK letter written as a surrogate pair
        assertEquals("_a${ext}b_", NoteFormat.wrap(v("a${ext}b", 0, 1), "_").text)
        assertEquals("_${ext}_ x", NoteFormat.wrap(v("$ext x", 2), "_").text)
    }

    @Test fun underscoreRunsDoNotCollapse() {
        val p = NoteFormat.Palette(Color.Gray, Color.Blue)
        assertEquals("Name: ______", NoteFormat.styled("Name: ________", p, forEditor = false).text)
        assertEquals("Name: ______", NoteFormat.plain("Name: ________"))
        assertEquals("_init_", NoteFormat.styled("__init__", p, forEditor = false).text)
        assertEquals("_init_", NoteFormat.plain("__init__"))
        assertEquals("Name: ________", NoteFormat.styled("Name: ________", p, forEditor = true).text)
        // A very long run neither recurses deeply nor shrinks to "__".
        val run = "_".repeat(20_000)
        assertEquals(run.length - 2, NoteFormat.plain(run).length)
        assertEquals(run.length - 2, NoteFormat.styled(run, p, forEditor = false).length)
        assertEquals(run, NoteFormat.styled(run, p, forEditor = true).text)
    }

    @Test fun nestedFormattingKeepsBothStyles() {
        val s = NoteFormat.styled("a **_both_** b", NoteFormat.Palette(Color.Gray, Color.Blue), forEditor = false)
        assertEquals("a both b", s.text)
        assertTrue(s.spanStyles.any { it.item.fontWeight == FontWeight.Bold && it.start == 2 && it.end == 6 })
        assertTrue(s.spanStyles.any { it.item.fontStyle == FontStyle.Italic && it.start == 2 && it.end == 6 })
        val e = NoteFormat.styled("a **_both_** b", NoteFormat.Palette(Color.Gray, Color.Blue), forEditor = true)
        assertTrue(e.spanStyles.any { it.item.fontStyle == FontStyle.Italic && it.start == 5 && it.end == 9 })
    }

    @Test fun plainIsWhatTheCardReads() {
        assertEquals("Buy organic milk\ncall\nTrip", NoteFormat.plain("Buy **organic** milk\n- [ ] call\n# Trip"))
        assertEquals("both", NoteFormat.plain("~~**both**~~"))
    }
}
