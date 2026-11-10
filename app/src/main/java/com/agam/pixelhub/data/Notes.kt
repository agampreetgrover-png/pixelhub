package com.agam.pixelhub.data

import kotlinx.coroutines.flow.Flow

/** Notes, last edited first. */
class NotesRepository(private val dao: NoteDao, private val now: () -> Long = System::currentTimeMillis) {
    val all: Flow<List<Note>> = dao.observeAll()

    /**
     * Saves note [id] (0 = a new one) and returns its id. A note left with no title and no body
     * is deleted instead (as Keep discards an empty note) and 0 comes back.
     */
    suspend fun save(id: Long, title: String, text: String): Long {
        val t = title.trim().take(MAX_TITLE)
        val b = clean(text).orEmpty()
        if (t.isEmpty() && b.isEmpty()) {
            if (id != 0L) dao.deleteById(id)
            return 0
        }
        val at = now()
        if (id == 0L) return dao.insert(Note(title = t, text = b, createdAt = at, updatedAt = at))
        dao.edit(id, t, b, at)
        return id
    }

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

        /** Trimmed and capped, or null when there's nothing to keep. */
        fun clean(text: String): String? = text.trim().take(MAX_LENGTH).trimEnd().takeIf { it.isNotEmpty() }
    }
}
