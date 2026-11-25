package com.agam.pixelhub.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.util.Locale

/** Someone you lend to or borrow from. [nameKey] keeps "Neha" and "neha" one person. */
@Entity(tableName = "people", indices = [Index(value = ["nameKey"], unique = true)])
data class Person(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val nameKey: String = keyOf(name),
    val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {
        fun keyOf(name: String): String = name.trim().lowercase(Locale.ROOT).replace(Regex("""\s+"""), " ")
    }
}

/**
 * One movement of money between you and a person. [amountPaise] is signed: positive when
 * they owe you more (you paid or lent), negative when you owe them more (they paid or lent).
 */
@Entity(tableName = "ledger_entries", indices = [Index("personId")])
data class LedgerRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long,
    val amountPaise: Long,
    val note: String = "",
    /** When it happened, epoch millis. */
    val at: Long,
)

/** A person with their running balance (positive: they owe you) and last activity. */
data class PersonBalance(
    @Embedded val person: Person,
    val balancePaise: Long,
    val lastAt: Long?,
)

@Dao
interface PeopleDao {
    /** Open balances first, then everyone else; most recently active first within each. */
    @Query(
        "SELECT p.*, COALESCE(SUM(l.amountPaise), 0) AS balancePaise, MAX(l.at) AS lastAt " +
            "FROM people p LEFT JOIN ledger_entries l ON l.personId = p.id GROUP BY p.id " +
            "ORDER BY (COALESCE(SUM(l.amountPaise), 0) = 0), COALESCE(MAX(l.at), p.createdAt) DESC",
    )
    fun observeWithBalances(): Flow<List<PersonBalance>>

    @Query("SELECT * FROM people ORDER BY name COLLATE NOCASE")
    suspend fun all(): List<Person>

    @Query("SELECT * FROM people WHERE nameKey = :key")
    suspend fun byKey(key: String): Person?

    @Query("SELECT * FROM people WHERE id = :id")
    suspend fun get(id: Long): Person?

    @Insert suspend fun insert(person: Person): Long
    @Update suspend fun update(person: Person)

    @Query("DELETE FROM people WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface LedgerDao {
    /** Newest first. */
    @Query("SELECT * FROM ledger_entries WHERE personId = :personId ORDER BY at DESC, id DESC")
    fun observeFor(personId: Long): Flow<List<LedgerRecord>>

    @Query("SELECT COALESCE(SUM(amountPaise), 0) FROM ledger_entries WHERE personId = :personId")
    suspend fun balance(personId: Long): Long

    @Insert suspend fun insert(record: LedgerRecord): Long
    @Update suspend fun update(record: LedgerRecord)
    @Delete suspend fun delete(record: LedgerRecord)

    @Query("DELETE FROM ledger_entries WHERE personId = :personId")
    suspend fun deleteFor(personId: Long)
}
