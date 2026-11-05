package com.agam.pixelhub.ledger

import android.database.sqlite.SQLiteConstraintException
import com.agam.pixelhub.data.LedgerDao
import com.agam.pixelhub.data.LedgerRecord
import com.agam.pixelhub.data.PeopleDao
import com.agam.pixelhub.data.Person
import com.agam.pixelhub.data.PersonBalance
import com.agam.pixelhub.expenses.Money
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/**
 * Who owes whom, one person at a time. Separate from Expenses on purpose: lending money or
 * being paid back isn't spending or income, so none of it touches totals or budgets.
 */
class LedgerRepository(private val people: PeopleDao, private val records: LedgerDao) {
    /** Everyone with their balance; open balances first. */
    fun balances(): Flow<List<PersonBalance>> = people.observeWithBalances()

    /** One person's entries, newest first. */
    fun entries(personId: Long): Flow<List<LedgerRecord>> = records.observeFor(personId)

    suspend fun names(): List<String> = people.all().map { it.name }

    suspend fun find(name: String): Person? = people.byKey(Person.keyOf(name))

    suspend fun person(id: Long): Person? = people.get(id)

    suspend fun balance(personId: Long): Long = records.balance(personId)

    /** The person called [name], added if new. */
    suspend fun addPerson(name: String): Person {
        val clean = LedgerMath.tidyName(name)
        find(clean)?.let { return it }
        return try {
            Person(name = clean).let { it.copy(id = people.insert(it)) }
        } catch (e: SQLiteConstraintException) {
            // Added by another write a moment ago.
            find(clean) ?: throw e
        }
    }

    /** Records money with [name] (added if new) and returns their new balance. */
    suspend fun record(name: String, signedPaise: Long, note: String, at: Long = System.currentTimeMillis()): Long {
        val p = addPerson(name)
        records.insert(LedgerRecord(personId = p.id, amountPaise = signedPaise, note = note.trim(), at = at))
        return records.balance(p.id)
    }

    /** Inserts (id 0) or updates an entry. */
    suspend fun save(record: LedgerRecord) {
        if (record.id == 0L) records.insert(record) else records.update(record)
    }

    suspend fun delete(record: LedgerRecord) = records.delete(record)

    /** Puts back a just-deleted entry (Undo), with its old id. */
    suspend fun restore(record: LedgerRecord) {
        records.insert(record)
    }

    /** False when someone else already has that name. */
    suspend fun rename(person: Person, newName: String): Boolean {
        val clean = LedgerMath.tidyName(newName)
        if (clean.isEmpty()) return false
        val other = find(clean)
        if (other != null && other.id != person.id) return false
        people.update(person.copy(name = clean, nameKey = Person.keyOf(clean)))
        return true
    }

    /** Removes the person and all their entries. */
    suspend fun deletePerson(person: Person) {
        records.deleteFor(person.id)
        people.delete(person.id)
    }
}

/** Pure ledger logic (unit-tested). */
object LedgerMath {
    private val SHARE_DATE = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

    /** "naruna  sharma" → "Naruna Sharma". */
    fun tidyName(name: String): String = name.trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }
        .joinToString(" ") { w -> w.replaceFirstChar { it.titlecase(Locale.ROOT) } }

    /** "Naruna owes you ₹230", "You owe Naruna ₹50" or "All square with Naruna". */
    fun balanceText(name: String, paise: Long): String = when {
        paise > 0 -> "$name owes you ${Money.format(paise)}"
        paise < 0 -> "You owe $name ${Money.format(-paise)}"
        else -> "All square with $name"
    }

    /** "owes you ₹230" / "you owe ₹50" / "All square": the short form for lists. */
    fun shortText(paise: Long): String = when {
        paise > 0 -> "owes you ${Money.format(paise)}"
        paise < 0 -> "you owe ${Money.format(-paise)}"
        else -> "All square"
    }

    /** What one entry did, from your side: "You paid ₹150" or "They paid ₹50". */
    fun entryText(name: String, paise: Long): String =
        if (paise >= 0) "You paid ${Money.format(paise)}" else "$name paid ${Money.format(-paise)}"

    /**
     * Balance after each entry, for entries given newest first (as stored); the first value
     * is the current balance.
     */
    fun runningBalances(newestFirst: List<LedgerRecord>): List<Long> {
        var total = newestFirst.sumOf { it.amountPaise }
        return newestFirst.map { r -> total.also { total -= r.amountPaise } }
    }

    /** The entries since the balance was last zero, oldest first: what the current balance is made of. */
    fun openEntries(newestFirst: List<LedgerRecord>): List<LedgerRecord> {
        val oldestFirst = newestFirst.reversed()
        var running = 0L
        var start = 0
        oldestFirst.forEachIndexed { i, r ->
            running += r.amountPaise
            if (running == 0L) start = i + 1
        }
        return oldestFirst.drop(start)
    }

    /**
     * The text for WhatsApp or SMS. When they owe you it lists what the balance is made of
     * (the latest [maxLines] entries); when you owe them it just says so.
     */
    fun shareMessage(
        name: String,
        balancePaise: Long,
        open: List<LedgerRecord>,
        zone: ZoneId = ZoneId.systemDefault(),
        maxLines: Int = 10,
    ): String {
        val first = name.substringBefore(' ')
        if (balancePaise <= 0) {
            return if (balancePaise == 0L) "Hi $first, we're all square." else "Hi $first, I owe you ${Money.format(-balancePaise)}. Will settle soon."
        }
        val lines = open.takeLast(maxLines).joinToString("\n") { r ->
            val date = Instant.ofEpochMilli(r.at).atZone(zone).format(SHARE_DATE)
            val what = r.note.ifBlank { if (r.amountPaise >= 0) "I paid" else "you paid" }
            val sign = if (r.amountPaise >= 0) "" else "−"
            "• $date · $what · $sign${Money.format(abs(r.amountPaise))}"
        }
        val more = if (open.size > maxLines) "\n…and ${open.size - maxLines} earlier" else ""
        return "Hi $first, quick reminder: you owe me ${Money.format(balancePaise)}." +
            (if (lines.isNotEmpty()) "\n$lines$more" else "")
    }
}
