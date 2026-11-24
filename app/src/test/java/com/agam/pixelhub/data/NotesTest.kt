package com.agam.pixelhub.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test fun dropsEmptyItemsAtTheEnd() {
        assertEquals("Groceries\n- [ ] milk", NotesRepository.clean("Groceries\n- [ ] milk\n- [ ] "))
        assertNull(NotesRepository.clean("- [ ] "))
        assertNull(NotesRepository.clean("# "))
        assertEquals("a", NotesRepository.clean("a\n- \n- [ ] "))
        // Only at the end; an empty item mid-note keeps its space.
        assertEquals("a\n- [ ] \nb", NotesRepository.clean("a\n- [ ] \nb"))
    }

    @Test fun capDoesNotSplitAnEmoji() {
        assertEquals("a".repeat(199), ("a".repeat(199) + "😀").takeSafe(200))
        assertEquals("ab😀", "ab😀c".takeSafe(4))
        assertEquals("ab", "ab".takeSafe(5))
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

    @Test fun searchLooksInTitleAndBody() {
        val n = Note(title = "Shopping", text = "Milk, eggs", createdAt = 0)
        assertTrue(NotesRepository.matches(n, "  "))
        assertTrue(NotesRepository.matches(n, "shop"))
        assertTrue(NotesRepository.matches(n, "EGGS"))
        assertFalse(NotesRepository.matches(n, "bread"))
    }

    @Test fun newNoteKeepsItsPin() = runBlocking {
        val dao = FakeNoteDao()
        val id = NotesRepository(dao) { 1L }.save(0, "", "wifi password", pinned = true)
        assertTrue(dao.rows[id]!!.pinned)
    }

    @Test fun newNoteKeepsItsColour() = runBlocking {
        val dao = FakeNoteDao()
        val id = NotesRepository(dao) { 1L }.save(0, "Ideas", "", color = 3)
        assertEquals(3, dao.rows[id]!!.color)
    }

    @Test fun rebuiltEditorDoesNotDuplicateANewNote() = runBlocking {
        val dao = FakeNoteDao()
        val repo = NotesRepository(dao) { 5L }
        // The old editor's last save, then the rebuilt editor's first save, both still at id 0.
        val first = repo.save(0, "", "milk", createdAt = 1_000L)
        val second = repo.save(0, "", "milk, eggs", pinned = true, createdAt = 1_000L)
        assertEquals(first, second)
        assertEquals(1, dao.rows.size)
        assertEquals("milk, eggs", dao.rows[first]!!.text)
        assertTrue(dao.rows[first]!!.pinned)
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
        override suspend fun edit(id: Long, title: String, text: String, at: Long, pinned: Boolean, color: Int) {
            rows[id] = rows.getValue(id).copy(title = title, text = text, updatedAt = at, pinned = pinned, color = color)
        }
        override suspend fun idCreatedAt(at: Long): Long? = rows.values.firstOrNull { it.createdAt == at }?.id
        override suspend fun deleteById(id: Long) { rows.remove(id) }
        override suspend fun setPinned(id: Long, pinned: Boolean) {
            rows[id] = rows.getValue(id).copy(pinned = pinned)
        }
        override suspend fun setColor(id: Long, color: Int) {
            rows[id] = rows.getValue(id).copy(color = color)
        }
    }
}
