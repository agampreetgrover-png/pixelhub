package com.agam.pixelhub.data

import kotlinx.coroutines.flow.Flow

/** Notes, pinned first, then last edited first. */
class NotesRepository(private val dao: NoteDao, private val now: () -> Long = System::currentTimeMillis) {
    val all: Flow<List<Note>> = dao.observeAll()

    /**
     * Saves note [id] (0 = a new one) and returns its id. A note left with no title and no body
     * is deleted instead (as Keep discards an empty note) and 0 comes back.
     */
    suspend fun save(id: Long, title: String, text: String, pinned: Boolean = false, color: Int = 0): Long {
        val t = title.trim().take(MAX_TITLE)
        val b = clean(text).orEmpty()
        if (t.isEmpty() && b.isEmpty()) {
            if (id != 0L) dao.deleteById(id)
            return 0
        }
        val at = now()
        // [pinned] and [color] count only for a new note; setPinned and setColor change them after.
        if (id == 0L) return dao.insert(Note(title = t, text = b, createdAt = at, updatedAt = at, pinned = pinned, color = color))
        dao.edit(id, t, b, at)
        return id
    }

    suspend fun setPinned(id: Long, pinned: Boolean) = dao.setPinned(id, pinned)

    suspend fun setColor(id: Long, color: Int) = dao.setColor(id, color)

    /** Deletes note [id] and returns it as it was, for Undo; null if it's already gone. */
    suspend fun delete(id: Long): Note? = dao.get(id)?.also { dao.delete(it) }

    /** Puts back a note that was just deleted (Undo), with its old id and place. */
    suspend fun restore(note: Note) {
        dao.insert(note)
    }

    companion object {
        /** Room for a long note; still caps a runaway paste. */
        const val MAX_LENGTH = 20_000
        const val MAX_TITLE = 200

        /** Whether [note] has [query] in its title or body, ignoring case; a blank query matches all. */
        fun matches(note: Note, query: String): Boolean {
            val q = query.trim()
            return q.isEmpty() || note.title.contains(q, ignoreCase = true) || note.text.contains(q, ignoreCase = true)
        }

        /** Trimmed and capped, or null when there's nothing to keep. */
        fun clean(text: String): String? = text.trim().take(MAX_LENGTH).trimEnd().takeIf { it.isNotEmpty() }
    }
}
