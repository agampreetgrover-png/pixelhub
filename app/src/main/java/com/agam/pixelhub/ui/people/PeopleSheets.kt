package com.agam.pixelhub.ui.people

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.agam.pixelhub.data.LedgerRecord
import com.agam.pixelhub.data.Person
import com.agam.pixelhub.expenses.Money
import com.agam.pixelhub.ledger.LedgerMath
import com.agam.pixelhub.ui.HubSheet
import com.agam.pixelhub.ui.HubTextField
import com.agam.pixelhub.ui.PrimaryButton
import com.agam.pixelhub.ui.QuietButton
import com.agam.pixelhub.ui.expenses.inkDatePickerColors
import com.agam.pixelhub.ui.motion.pressable
import com.agam.pixelhub.ui.theme.Hub
import com.agam.pixelhub.ui.theme.HubType
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private val ChipDate: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

/** "250" or "99.5" for a paise amount, for editing. */
private fun plainAmount(paise: Long): String = BigDecimal.valueOf(paise, 2).stripTrailingZeros().toPlainString()

/**
 * Add or edit one entry with [name]: who paid, how much, what for, and when. [youPaid] is the
 * starting direction. Saving gives back [initial] with the signed amount, note and time filled in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LedgerEntrySheet(
    name: String,
    initial: LedgerRecord,
    youPaid: Boolean,
    onDismiss: () -> Unit,
    onSave: (LedgerRecord) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val c = Hub.colors
    val first = name.substringBefore(' ')
    val zone = ZoneId.systemDefault()
    val startAt = remember { Instant.ofEpochMilli(initial.at).atZone(zone) }
    val startAmount = if (initial.amountPaise != 0L) plainAmount(abs(initial.amountPaise)) else ""

    var paid by rememberSaveable { mutableStateOf(youPaid) }
    var amount by rememberSaveable { mutableStateOf(startAmount) }
    var note by rememberSaveable { mutableStateOf(initial.note) }
    var date by rememberSaveable { mutableStateOf(startAt.toLocalDate()) }
    // Saved, like the expense editor's: rotating with the date picker open keeps it open.
    var showDate by rememberSaveable { mutableStateOf(false) }
    val paise = Money.parse(amount)
    val today = LocalDate.now()
    val dirty = amount != startAmount || note != initial.note || paid != youPaid || date != startAt.toLocalDate()

    HubSheet(onDismiss = onDismiss, dirty = dirty) {
        // Scrolls on its own: in landscape or at a large font, the keyboard would otherwise hide "Add".
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Text(if (initial.id == 0L) "New entry with $first" else "Edit entry", style = HubType.title, color = c.ink)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Choice("I paid or lent", selected = paid, onClick = { paid = true }, modifier = Modifier.weight(1f))
                Choice("$first paid or lent", selected = !paid, onClick = { paid = false }, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            HubTextField(
                value = amount,
                // Too long is refused, not cut: cutting a pasted "1,00,00,000.50" would quietly
                // save a different amount.
                onValueChange = { v ->
                    val kept = v.filter { it.isDigit() || it == '.' || it == ',' }
                    if (kept.length <= 16 && kept.count(Char::isDigit) <= 12) amount = kept
                },
                label = "Amount (₹)",
                isError = amount.isNotEmpty() && paise == null,
                supportingText = paise?.let { p ->
                    if (paid) "$first will owe you ${Money.format(p)} more" else "You'll owe $first ${Money.format(p)} more"
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            HubTextField(
                value = note,
                onValueChange = { note = it.take(80) },
                label = "What for (optional)",
                placeholder = "Lunch, auto, movie…",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                val other = date != today && date != today.minusDays(1)
                Choice("Today", selected = date == today, onClick = { date = today }, modifier = Modifier.weight(1f))
                Choice("Yesterday", selected = date == today.minusDays(1), onClick = { date = today.minusDays(1) }, modifier = Modifier.weight(1f))
                Choice(if (other) date.format(ChipDate) else "Pick date", selected = other, onClick = { showDate = true }, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(20.dp))
            PrimaryButton(
                if (initial.id == 0L) "Add" else "Save",
                enabled = paise != null && (initial.id == 0L || dirty),
                onClick = {
                    val p = paise ?: return@PrimaryButton
                    // Keep the entry's own time of day; a new one today is "now".
                    val at = date.atTime(startAt.toLocalTime()).atZone(zone).toInstant().toEpochMilli()
                        .coerceAtMost(System.currentTimeMillis())
                    onSave(initial.copy(amountPaise = if (paid) p else -p, note = note.trim(), at = at))
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (onDelete != null) {
                QuietButton("Delete entry", onClick = onDelete, color = c.danger, icon = Icons.Outlined.Delete, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }

    if (showDate) {
        val todayUtc = remember { today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = remember {
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayUtc
                    override fun isSelectableYear(year: Int) = year <= today.year
                }
            },
        )
        val colors = inkDatePickerColors()
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                QuietButton("Set date", color = c.ink, onClick = {
                    state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    showDate = false
                })
            },
            dismissButton = { QuietButton("Cancel", color = c.inkMuted, onClick = { showDate = false }) },
            shape = RoundedCornerShape(32.dp),
            tonalElevation = 0.dp,
            colors = colors,
        ) {
            DatePicker(state = state, colors = colors)
        }
    }
}

/**
 * Add or rename a person. [takenName] is a name that was refused because someone else has it:
 * the error shows while the field still says that name and clears as soon as it's changed.
 */
@Composable
internal fun NameSheet(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    takenName: String? = null,
) {
    var name by rememberSaveable { mutableStateOf(initial) }
    val clean = LedgerMath.tidyName(name)
    val taken = takenName != null && Person.keyOf(clean) == Person.keyOf(takenName)
    val error = if (taken) "Someone else already has that name" else null
    val canSave = clean.isNotEmpty() && clean != initial && !taken
    HubSheet(onDismiss = onDismiss, dirty = name != initial) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Text(title, style = HubType.title, color = Hub.colors.ink)
            Spacer(Modifier.height(16.dp))
            HubTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                label = "Name",
                isError = error != null,
                supportingText = error,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (canSave) onSave(clean) }),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))
            PrimaryButton("Save", enabled = canSave, onClick = { onSave(clean) }, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** A pill that is one of a set of choices: ink when selected. */
@Composable
private fun Choice(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Hub.colors
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) c.ink else c.raised2)
            .pressable(onClick = onClick, pressedScale = 0.97f, role = Role.RadioButton, showRipple = false)
            .semantics { this.selected = selected }
            .heightIn(min = 48.dp)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = HubType.button, color = if (selected) c.ground else c.ink, maxLines = 2)
    }
}
