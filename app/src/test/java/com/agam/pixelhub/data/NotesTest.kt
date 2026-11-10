package com.agam.pixelhub.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test fun emptyNoteIsNotKept() = runBlocking {
        val dao = FakeNoteDao()
        val repo = NotesRepository(dao) { 1L }
        assertEquals(0L, repo.save(0, "  ", "\n"))
        assertTrue(dao.rows.isEmpty())

        val id = repo.save(0, "Shopping", "milk")
        assertEquals("Shopping", dao.rows[id]!!.title)
        assertEquals(0L, repo.save(id, "", " "))
        assertTrue(dao.rows.isEmpty())
    }

    private class FakeNoteDao : NoteDao {
        val rows = mutableMapOf<Long, Note>()
        override fun observeAll(): Flow<List<Note>> = flowOf(rows.values.toList())
        override suspend fun get(id: Long): Note? = rows[id]
        override suspend fun insert(note: Note): Long {
            val id = if (note.id != 0L) note.id else rows.size + 1L
            rows[id] = note.copy(id = id)
            return id
        }
        override suspend fun delete(note: Note) { rows.remove(note.id) }
        override suspend fun edit(id: Long, title: String, text: String, at: Long) {
            rows[id] = rows.getValue(id).copy(title = title, text = text, updatedAt = at)
        }
        override suspend fun deleteById(id: Long) { rows.remove(id) }
    }
}
