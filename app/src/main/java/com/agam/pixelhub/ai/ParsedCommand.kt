package com.agam.pixelhub.ai

import com.agam.pixelhub.data.ExpenseCategory
import com.agam.pixelhub.data.ExpenseKind
import com.agam.pixelhub.scheduling.Recurrence
import java.time.LocalDateTime

/** What the assistant wants to do. Always shown to the user for confirmation first. */
sealed interface ParsedCommand {
    data class CreateReminder(
        val title: String,
        val at: LocalDateTime,
        val recurrence: Recurrence,
        val notes: String = "",
    ) : ParsedCommand

    data class CreateAlarm(val hour: Int, val minute: Int, val daysMask: Int, val label: String) : ParsedCommand

    /** Money spent or received. [at] null = "now" (decided when the user confirms). */
    data class LogExpense(
        val amountPaise: Long,
        val category: ExpenseCategory,
        val note: String,
        val at: LocalDateTime? = null,
        val kind: ExpenseKind = ExpenseKind.EXPENSE,
    ) : ParsedCommand {
        val isIncome: Boolean get() = kind == ExpenseKind.INCOME
    }

    /**
     * Money between you and [name] for the People ledger. [signedPaise] is positive when they
     * owe you more (you paid or lent), negative when you owe them more. [isNewPerson]: [name]
     * isn't saved yet and is added when this is confirmed.
     */
    data class LedgerEntry(
        val name: String,
        val signedPaise: Long,
        val note: String = "",
        val at: LocalDateTime? = null,
        val isNewPerson: Boolean = false,
    ) : ParsedCommand

    /** "naruna ka hisaab": answered at once from the ledger, no card. */
    data class LedgerBalance(val name: String) : ParsedCommand

    data class OpenApp(val query: String) : ParsedCommand
    data class OpenLink(val uri: String) : ParsedCommand

    /** A plain reply, e.g. the help text. */
    data class Answer(val text: String) : ParsedCommand

    /** The request wasn't understood; [reason] is shown to the user as-is. */
    data class Invalid(val reason: String) : ParsedCommand
}

/** The parser's answer plus the rule that produced it, for the Settings log. */
data class ParseResult(val command: ParsedCommand, val rule: String) {
    val ok: Boolean get() = command !is ParsedCommand.Invalid
}
