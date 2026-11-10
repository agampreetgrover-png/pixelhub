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

    // Bold before italic; "_" only at word edges, so snake_case stays plain.
    private val Inline = Regex("""\*\*(.+?)\*\*|~~(.+?)~~|(?<!\w)_(.+?)_(?!\w)""")

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

    private fun AnnotatedString.Builder.inline(s: String, p: Palette, forEditor: Boolean) {
        var i = 0
        for (m in Inline.findAll(s)) {
            append(s, i, m.range.first)
            val (body, mark, style) = when {
                m.groups[1] != null -> Triple(m.groupValues[1], 2, SpanStyle(fontWeight = FontWeight.Bold))
                m.groups[2] != null -> Triple(m.groupValues[2], 2, SpanStyle(textDecoration = TextDecoration.LineThrough))
                else -> Triple(m.groupValues[3], 1, SpanStyle(fontStyle = FontStyle.Italic))
            }
            val marker = s.substring(m.range.first, m.range.first + mark)
            if (forEditor) withStyle(SpanStyle(color = p.muted)) { append(marker) }
            withStyle(style) { append(body) }
            if (forEditor) withStyle(SpanStyle(color = p.muted)) { append(marker) }
            i = m.range.last + 1
        }
        append(s, i, s.length)
    }

    /**
     * Wraps the selection in [mark] ("**", "_" or "~~"), or unwraps it if it's wrapped already.
     * With nothing selected, drops in an empty pair with the cursor between.
     */
    fun wrap(v: TextFieldValue, mark: String): TextFieldValue {
        val t = v.text
        val s = v.selection.min
        val e = v.selection.max
        val m = mark.length
        val sel = t.substring(s, e)
        // The markers just outside the selection, or the selection's own ends.
        if (s >= m && e + m <= t.length && t.startsWith(mark, s - m) && t.startsWith(mark, e)) {
            return TextFieldValue(t.substring(0, s - m) + sel + t.substring(e + m), TextRange(s - m, e - m))
        }
        if (sel.length >= 2 * m && sel.startsWith(mark) && sel.endsWith(mark)) {
            return TextFieldValue(t.substring(0, s) + sel.substring(m, sel.length - m) + t.substring(e), TextRange(s, e - 2 * m))
        }
        return TextFieldValue(t.substring(0, s) + mark + sel + mark + t.substring(e), TextRange(s + m, e + m))
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
        val off = lines.all { same(blockOf(it)) }
        val out = lines.joinToString("\n") { line ->
            val bare = line.substring(blockOf(line)?.prefix?.length ?: 0)
            if (off) bare else block.prefix + bare
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
