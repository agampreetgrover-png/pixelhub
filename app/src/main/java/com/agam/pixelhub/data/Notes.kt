package com.agam.pixelhub.data

import kotlinx.coroutines.flow.Flow

/** Quick notes kept in the drawer. */
class NotesRepository(private val dao: NoteDao, private val now: () -> Long = System::currentTimeMillis) {
    /** Open notes first, newest first. */
    val all: Flow<List<Note>> = dao.observeAll()

    /** Adds [text] as a new note; blank text adds nothing. */
    suspend fun add(text: String): Note? {
        val t = clean(text) ?: return null
        val note = Note(text = t, createdAt = now())
        return note.copy(id = dao.insert(note))
    }

    suspend fun setDone(note: Note, done: Boolean) = dao.update(note.copy(done = done))

    suspend fun delete(note: Note) = dao.delete(note)

    /** Puts back a note that was just deleted (Undo), with its old id and place. */
    suspend fun restore(note: Note) {
        dao.insert(note)
    }

    suspend fun clearDone() = dao.deleteDone()

    companion object {
        /** Long enough for an address or a list, short enough that a stray paste can't bloat the drawer. */
        const val MAX_LENGTH = 2_000

        /** Trimmed and capped, or null when there's nothing to keep. */
        fun clean(text: String): String? = text.trim().take(MAX_LENGTH).trimEnd().takeIf { it.isNotEmpty() }
    }
}
