package com.agam.pixelhub.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Notes, pinned first, then last edited first. */
class NotesRepository(private val dao: NoteDao, private val now: () -> Long = System::currentTimeMillis) {
    val all: Flow<List<Note>> = dao.observeAll()

    /** One save at a time, so two first saves of the same new note can't both insert. */
    private val lock = Mutex()

    /**
     * Saves note [id] (0 = a new one, known by [createdAt]) with the editor's title, body, pin and
     * colour, and returns its id. A note left with no title and no body is deleted instead (as
     * Keep discards an empty note) and 0 comes back.
     */
    suspend fun save(
        id: Long,
        title: String,
        text: String,
        pinned: Boolean = false,
        color: Int = 0,
        createdAt: Long = 0L,
    ): Long = lock.withLock {
        val t = title.trim().take(MAX_TITLE)
        val b = clean(text).orEmpty()
        val row = resolve(id, createdAt)
        if (t.isEmpty() && b.isEmpty()) {
            if (row != 0L) dao.deleteById(row)
            return@withLock 0L
        }
        val at = now()
        if (row == 0L) {
            return@withLock dao.insert(
                Note(title = t, text = b, createdAt = createdAt.takeIf { it != 0L } ?: at, updatedAt = at, pinned = pinned, color = color),
            )
        }
        dao.edit(row, t, b, at, pinned, color)
        row
    }

    /** Pins note [id] (or the new note started at [createdAt]); a note not saved yet takes its pin in with its first save. */
    suspend fun setPinned(id: Long, pinned: Boolean, createdAt: Long = 0L) = lock.withLock {
        resolve(id, createdAt).takeIf { it != 0L }?.let { dao.setPinned(it, pinned) }
    }

    suspend fun setColor(id: Long, color: Int, createdAt: Long = 0L) = lock.withLock {
        resolve(id, createdAt).takeIf { it != 0L }?.let { dao.setColor(it, color) }
    }

    /**
     * The row for [id], or for the new note started at [createdAt]: an editor rebuilt mid-typing
     * (rotation, dark mode) still has id 0 though its first save went in. 0 when there's none.
     */
    private suspend fun resolve(id: Long, createdAt: Long): Long = when {
        id != 0L -> id
        createdAt != 0L -> dao.idCreatedAt(createdAt) ?: 0L
        else -> 0L
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

        /** Whether [note] has [query] in its title or body, ignoring case; a blank query matches all. */
        fun matches(note: Note, query: String): Boolean {
            val q = query.trim()
            return q.isEmpty() || note.title.contains(q, ignoreCase = true) || note.text.contains(q, ignoreCase = true)
        }

        /** Trimmed and capped, or null when there's nothing to keep. */
        fun clean(text: String): String? = text.trim().take(MAX_LENGTH).trimEnd().takeIf { it.isNotEmpty() }
    }
}
