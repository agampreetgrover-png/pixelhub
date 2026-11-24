package com.agam.pixelhub.ui.notes

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import java.util.regex.Matcher

/**
 * The light Markdown notes are written in: "# " and "## " headings, "- " bullets, "- [ ] " and
 * "- [x] " checklists, and **bold**, _italic_ and ~~strike~~ inside a line. The text stays
 * plain, so search and share see exactly what was typed.
 */
object NoteFormat {
    enum class Block(val prefix: String) { H1("# "), H2("## "), BULLET("- "), CHECK("- [ ] "), DONE("- [x] ") }

    /** The block [line] starts with, if any. */
    fun blockOf(line: String): Block? = when {
        line.startsWith("# ") -> Block.H1
        line.startsWith("## ") -> Block.H2
        line.startsWith("- [ ] ") -> Block.CHECK
        line.startsWith("- [x] ") || line.startsWith("- [X] ") -> Block.DONE
        line.startsWith("- ") -> Block.BULLET
        else -> null
    }

    // Bold before italic; "_" only at word edges, so snake_case stays plain. The word class is
    // spelled out (not \w) so the JVM, where \w is ASCII, agrees with Android, where it's Unicode.
    private val Inline = Regex("""\*\*(.+?)\*\*|~~(.+?)~~|(?<![\p{L}\p{M}\p{Nd}_])_(.+?)_(?![\p{L}\p{M}\p{Nd}_])""")
    private val InlineP = Inline.toPattern()

    class Palette(val muted: Color, val accent: Color)

    /**
     * [text] with its formatting. [forEditor] keeps every character, greying the markers, so the
     * cursor's offsets still line up; otherwise markers are dropped and lists drawn as • ☐ ☑.
     */
    fun styled(text: String, p: Palette, forEditor: Boolean): AnnotatedString = buildAnnotatedString {
        text.split('\n').forEachIndexed { i, line ->
            if (i > 0) append('\n')
            val start = length
            val block = blockOf(line)
            val prefix = block?.prefix?.length ?: 0
            if (forEditor) {
                if (block != null) {
                    val tick = block == Block.CHECK || block == Block.DONE
                    withStyle(SpanStyle(color = if (tick) p.accent else p.muted, fontWeight = if (tick) FontWeight.Bold else null)) {
                        append(line, 0, prefix)
                    }
                }
            } else {
                when (block) {
                    Block.BULLET -> append("•  ")
                    Block.CHECK -> append("☐  ")
                    Block.DONE -> append("☑  ")
                    else -> {}
                }
            }
            val content = length
            inline(line.substring(prefix), p, forEditor)
            when (block) {
                Block.H1 -> addStyle(SpanStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold), start, length)
                Block.H2 -> addStyle(SpanStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold), start, length)
                Block.DONE -> addStyle(SpanStyle(color = p.muted, textDecoration = TextDecoration.LineThrough), content, length)
                else -> {}
            }
        }
    }

    // Matches inside [from]..[to] of the whole line, its lookarounds seeing past the ends, so a body
    // reads just as it does in the full line: a run like "______" never nests into itself.
    private fun inlineMatcher(s: String, from: Int, to: Int): Matcher =
        InlineP.matcher(s).region(from, to).useTransparentBounds(true)

    // Only one group matches at a time.
    private fun Matcher.bodyGroup() = when { start(1) >= 0 -> 1; start(2) >= 0 -> 2; else -> 3 }

    private fun AnnotatedString.Builder.inline(s: String, p: Palette, forEditor: Boolean, from: Int = 0, to: Int = s.length) {
        val m = inlineMatcher(s, from, to)
        var i = from
        while (m.find()) {
            append(s, i, m.start())
            val g = m.bodyGroup()
            val style = when (g) {
                1 -> SpanStyle(fontWeight = FontWeight.Bold)
                2 -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                else -> SpanStyle(fontStyle = FontStyle.Italic)
            }
            if (forEditor) withStyle(SpanStyle(color = p.muted)) { append(s, m.start(), m.start(g)) }
            // The body may hold more formatting, as in **_both_**.
            withStyle(style) { inline(s, p, forEditor, m.start(g), m.end(g)) }
            if (forEditor) withStyle(SpanStyle(color = p.muted)) { append(s, m.end(g), m.end()) }
            i = m.end()
        }
        append(s, i, to)
    }

    /** [text] as a card reads it: markers and list or heading marks dropped, for search. */
    fun plain(text: String): String = text.split('\n').joinToString("\n") { line ->
        plainInline(line.substring(blockOf(line)?.prefix?.length ?: 0))
    }

    private fun plainInline(s: String, from: Int = 0, to: Int = s.length): String {
        val m = inlineMatcher(s, from, to)
        val out = StringBuilder()
        var i = from
        while (m.find()) {
            val g = m.bodyGroup()
            out.append(s, i, m.start()).append(plainInline(s, m.start(g), m.end(g)))
            i = m.end()
        }
        return out.append(s, i, to).toString()
    }

    private val Marks = setOf(Character.NON_SPACING_MARK, Character.COMBINING_SPACING_MARK, Character.ENCLOSING_MARK)

    // The italic pattern's word class above: letters, marks (accents, matras), digits and '_'.
    // By code point, as the regex reads it, so a letter outside the BMP (a surrogate pair) counts too.
    private fun isWord(cp: Int) = cp == '_'.code || Character.isLetterOrDigit(cp) || Character.getType(cp).toByte() in Marks

    /**
     * Wraps the selection in [mark] ("**", "_" or "~~"), or unwraps it if it's wrapped already.
     * With nothing selected, drops in an empty pair with the cursor between (italic takes in the
     * word the cursor touches, and the cursor stays where it was inside it).
     */
    fun wrap(v: TextFieldValue, mark: String): TextFieldValue {
        val t = v.text
        val s = v.selection.min
        val e = v.selection.max
        val m = mark.length
        val sel = t.substring(s, e)
        // styled() reads one line at a time, so a pair can't span lines: each line gets its own.
        if ('\n' in sel) return wrapLines(t, s, e, mark)
        // The markers just outside the selection, or the selection's own ends.
        if (s >= m && e + m <= t.length && t.startsWith(mark, s - m) && t.startsWith(mark, e)) {
            return TextFieldValue(t.substring(0, s - m) + sel + t.substring(e + m), TextRange(s - m, e - m))
        }
        if (sel.length >= 2 * m && sel.startsWith(mark) && sel.endsWith(mark)) {
            return TextFieldValue(t.substring(0, s) + sel.substring(m, sel.length - m) + t.substring(e), TextRange(s, e - 2 * m))
        }
        // Already inside italic ("_foo|bar_", "_hello world|_"): take that pair off rather than wrap again.
        if (mark == "_") italicAround(t, s, e)?.let { (a, b) ->
            fun shift(x: Int) = x - (if (x > a) m else 0) - (if (x >= b) m else 0)
            return TextFieldValue(t.substring(0, a) + t.substring(a + m, b - m) + t.substring(b), TextRange(shift(s), shift(e)))
        }
        val (a, b) = growToWord(t, s, e, mark)
        val out = t.substring(0, a) + mark + t.substring(a, b) + mark + t.substring(b)
        // A bare cursor stays a bare cursor, now inside the pair; selecting the word would let the next key overwrite it.
        return TextFieldValue(out, if (s == e) TextRange(s + m) else TextRange(a + m, b + m))
    }

    /** "_" only counts at word edges, so italic takes in the whole word on either side of [s]..[e]. */
    private fun growToWord(t: String, s: Int, e: Int, mark: String): Pair<Int, Int> {
        var a = s
        var b = e
        if (mark == "_") {
            while (a > 0) {
                val cp = Character.codePointBefore(t, a)
                if (!isWord(cp)) break
                a -= Character.charCount(cp)
            }
            while (b < t.length) {
                val cp = Character.codePointAt(t, b)
                if (!isWord(cp)) break
                b += Character.charCount(cp)
            }
        }
        return a to b
    }

    /**
     * The italic pair around [s]..[e], as styled() reads the line (looking inside bold and strike
     * too), as its opening marker's offset and the offset just past its closing one; else null.
     */
    private fun italicAround(t: String, s: Int, e: Int): Pair<Int, Int>? {
        val lineStart = t.lastIndexOf('\n', s - 1) + 1
        val lineEnd = t.indexOf('\n', e).let { if (it < 0) t.length else it }
        val from = lineStart + (blockOf(t.substring(lineStart, lineEnd))?.prefix?.length ?: 0)
        if (s < from) return null
        val line = t.substring(from, lineEnd)
        return italicIn(line, 0, line.length, s - from, e - from)?.let { (a, b) -> a + from to b + from }
    }

    private fun italicIn(line: String, from: Int, to: Int, s: Int, e: Int): Pair<Int, Int>? {
        val m = inlineMatcher(line, from, to)
        while (m.find()) {
            if (m.start() > s) break
            if (e > m.end()) continue
            val g = m.bodyGroup()
            if (g == 3) return m.start() to m.end()
            italicIn(line, m.start(g), m.end(g), s, e)?.let { return it }
        }
        return null
    }

    /**
     * [wrap] for a selection over several lines: each line's words get their own pair, leaving a
     * line's list or heading mark and its edge spaces outside. Unwraps if every line is wrapped.
     */
    private fun wrapLines(t: String, s0: Int, e0: Int, mark: String): TextFieldValue {
        val m = mark.length
        val (s, e) = growToWord(t, s0, e0, mark)
        var at = s
        // Each line as (before, words, after).
        val parts = t.substring(s, e).split('\n').map { seg ->
            val lead = if (at == 0 || t[at - 1] == '\n') {
                val lineEnd = t.indexOf('\n', at).let { if (it < 0) t.length else it }
                minOf(seg.length, blockOf(t.substring(at, lineEnd))?.prefix?.length ?: 0)
            } else 0
            at += seg.length + 1
            val rest = seg.substring(lead)
            val words = rest.trim()
            val pre = lead + (rest.length - rest.trimStart().length)
            Triple(seg.substring(0, pre), words, seg.substring(pre + words.length))
        }
        val filled = parts.filter { it.second.isNotEmpty() }
        val off = filled.isNotEmpty() && filled.all { it.second.length >= 2 * m && it.second.startsWith(mark) && it.second.endsWith(mark) }
        val out = parts.joinToString("\n") { (pre, words, post) ->
            pre + when {
                words.isEmpty() -> words
                off -> words.substring(m, words.length - m)
                else -> mark + words + mark
            } + post
        }
        return TextFieldValue(t.substring(0, s) + out + t.substring(e), TextRange(s, s + out.length))
    }

    /**
     * Puts [block] on every line the selection touches, or takes it off if they all have it.
     * A line's old heading or list mark is swapped, not stacked.
     */
    fun toggleBlock(v: TextFieldValue, block: Block): TextFieldValue {
        val t = v.text
        val from = t.lastIndexOf('\n', v.selection.min - 1) + 1
        val to = t.indexOf('\n', v.selection.max).let { if (it < 0) t.length else it }
        val lines = t.substring(from, to).split('\n')
        fun same(b: Block?) = b == block || (block == Block.CHECK && b == Block.DONE)
        val multi = lines.size > 1
        // Over several lines, blank ones neither count nor turn into empty items.
        val marked = if (multi) lines.filter { it.isNotEmpty() } else lines
        val off = marked.isNotEmpty() && marked.all { same(blockOf(it)) }
        val out = lines.joinToString("\n") { line ->
            val b = blockOf(line)
            val bare = line.substring(b?.prefix?.length ?: 0)
            when {
                off -> bare
                multi && line.isEmpty() -> line
                same(b) -> line // a ticked item stays ticked
                else -> block.prefix + bare
            }
        }
        val text = t.substring(0, from) + out + t.substring(to)
        // Keep the cursor on the same words: shift by the first line's change, and the end by the total.
        val oldFirst = blockOf(lines.first())?.prefix?.length ?: 0
        val newFirst = blockOf(out.substringBefore('\n'))?.prefix?.length ?: 0
        val start = maxOf(from + newFirst, v.selection.min + newFirst - oldFirst).coerceAtMost(text.length)
        val end = maxOf(start, v.selection.max + out.length - (to - from)).coerceAtMost(text.length)
        return TextFieldValue(text, TextRange(start, end))
    }

    /**
     * After Enter on a list line ([old] -> [new]), starts the next item; Enter on an empty item
     * ends the list instead, as in Keep. Any other change comes back untouched.
     */
    fun continueList(old: TextFieldValue, new: TextFieldValue): TextFieldValue {
        val at = new.selection.start - 1
        if (!new.selection.collapsed || new.text.length != old.text.length + 1 || at < 0 || new.text[at] != '\n') return new
        if (new.text.removeRange(at, at + 1) != old.text) return new
        val lineStart = new.text.lastIndexOf('\n', at - 1) + 1
        val line = new.text.substring(lineStart, at)
        val block = blockOf(line)?.takeIf { it == Block.BULLET || it == Block.CHECK || it == Block.DONE } ?: return new
        val lineEnd = new.text.indexOf('\n', at + 1).let { if (it < 0) new.text.length else it }
        // Only a truly empty item ends the list; Enter just after "- " in "- milk" splits the line.
        if (line.length == block.prefix.length && lineEnd == at + 1) {
            return TextFieldValue(new.text.removeRange(lineStart, at + 1), TextRange(lineStart))
        }
        val next = if (block == Block.BULLET) Block.BULLET.prefix else Block.CHECK.prefix
        return TextFieldValue(new.text.substring(0, at + 1) + next + new.text.substring(at + 1), TextRange(at + 1 + next.length))
    }

    /** If [offset] falls on a checklist line's box ("- [ ]"), the text with it ticked or unticked; else null. */
    fun toggleCheckAt(text: String, offset: Int): String? {
        val lineStart = text.lastIndexOf('\n', offset - 1) + 1
        val lineEnd = text.indexOf('\n', lineStart).let { if (it < 0) text.length else it }
        val block = blockOf(text.substring(lineStart, lineEnd))
        if ((block != Block.CHECK && block != Block.DONE) || offset - lineStart > 5) return null
        return text.replaceRange(lineStart + 3, lineStart + 4, if (block == Block.CHECK) "x" else " ")
    }
}
