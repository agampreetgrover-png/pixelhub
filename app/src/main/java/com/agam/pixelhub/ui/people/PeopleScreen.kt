package com.agam.pixelhub.ui.people

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agam.pixelhub.container
import com.agam.pixelhub.data.LedgerRecord
import com.agam.pixelhub.data.Person
import com.agam.pixelhub.data.PersonBalance
import com.agam.pixelhub.expenses.Money
import com.agam.pixelhub.ledger.LedgerMath
import com.agam.pixelhub.ui.BackButton
import com.agam.pixelhub.ui.CircleIconButton
import com.agam.pixelhub.ui.EmptyState
import com.agam.pixelhub.ui.Gutter
import com.agam.pixelhub.ui.HubListRow
import com.agam.pixelhub.ui.PrimaryButton
import com.agam.pixelhub.ui.QuietButton
import com.agam.pixelhub.ui.ScreenScaffold
import com.agam.pixelhub.ui.expenses.incomeSoft
import com.agam.pixelhub.ui.hubCard
import com.agam.pixelhub.ui.motion.hubAnimateItem
import com.agam.pixelhub.ui.motion.pressable
import com.agam.pixelhub.ui.theme.Hub
import com.agam.pixelhub.ui.theme.HubColors
import com.agam.pixelhub.ui.theme.HubType
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private val ShortDate: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
private val ShortDateYear: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

/** "6 Nov", with the year once it isn't this year: a ledger runs for years, and "6 Nov" alone is ambiguous. */
private fun shortDate(d: LocalDate): String = d.format(if (d.year == LocalDate.now().year) ShortDate else ShortDateYear)

/** Keeps the open entry sheet (the record and who paid) across rotation. */
private val EditingSaver = Saver<Pair<LedgerRecord, Boolean>?, ArrayList<Any>>(
    save = { p -> p?.let { (r, paid) -> arrayListOf(r.id, r.personId, r.amountPaise, r.note, r.at, paid) } },
    restore = { l ->
        LedgerRecord(id = l[0] as Long, personId = l[1] as Long, amountPaise = l[2] as Long, note = l[3] as String, at = l[4] as Long) to
            (l[5] as Boolean)
    },
)

/** Green when they owe you, red when you owe them, muted when square. */
private fun HubColors.balanceColor(paise: Long): Color = when {
    paise > 0 -> ok
    paise < 0 -> danger
    else -> inkMuted
}

/**
 * Who owes whom: everyone you lend to or borrow from, with the running balance. Tap a person
 * for their history. Kept apart from Expenses: none of this counts as spending or income.
 */
@Composable
fun PeopleScreen(onBack: () -> Unit) {
    val app = LocalContext.current.container
    val balancesFlow = remember { app.ledger.balances() }
    val balances by balancesFlow.collectAsStateWithLifecycle(initialValue = null)
    var openId by rememberSaveable { mutableStateOf<Long?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }

    val open = openId?.let { id -> balances?.firstOrNull { it.person.id == id } }
    if (openId != null && (open != null || balances == null)) {
        BackHandler { openId = null }
        open?.let { PersonPage(it, onBack = { openId = null }) }
        return
    }

    ScreenScaffold(
        title = "People",
        navigationIcon = { BackButton(onBack) },
        actions = { CircleIconButton(Icons.Outlined.PersonAdd, "Add person", onClick = { adding = true }) },
    ) { padding ->
        val list = balances.orEmpty()
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = Gutter, end = Gutter, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "totals") { TotalsCard(list, modifier = hubAnimateItem(fadeIn = false)) }
            if (balances != null && list.isEmpty()) {
                item(key = "empty") {
                    Column(hubAnimateItem().fillMaxWidth().hubCard(Hub.colors.raised).padding(bottom = 20.dp)) {
                        EmptyState(
                            title = "No one here yet",
                            body = "Add the people you lend to or borrow from, or tell the assistant “paid Naruna 30”.",
                            accent = Hub.colors.ok,
                            action = { PrimaryButton("Add person", onClick = { adding = true }, icon = Icons.Outlined.PersonAdd) },
                        )
                    }
                }
            } else if (list.isNotEmpty()) {
                item(key = "list") {
                    Column(hubAnimateItem().fillMaxWidth().hubCard(Hub.colors.raised).padding(4.dp)) {
                        list.forEach { pb -> PersonRow(pb, onClick = { openId = pb.person.id }) }
                    }
                }
            }
        }
    }

    if (adding) {
        NameSheet(
            title = "Add person",
            initial = "",
            onDismiss = { adding = false },
            onSave = { name ->
                adding = false
                app.appScope.launch { openId = app.ledger.addPerson(name).id }
            },
        )
    }
}

/** Owed to you, you owe, and the net across everyone. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TotalsCard(list: List<PersonBalance>, modifier: Modifier = Modifier) {
    val c = Hub.colors
    val owedToYou = list.sumOf { it.balancePaise.coerceAtLeast(0) }
    val youOwe = list.sumOf { (-it.balancePaise).coerceAtLeast(0) }
    // Wraps a stat onto the next line when lakh-sized amounts or a large font don't fit, instead
    // of cutting a number off mid-digit (which reads as a different amount).
    FlowRow(
        modifier.fillMaxWidth().hubCard(c.raised).padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Stat("Owed to you", Money.format(owedToYou), c.ok)
        Stat("You owe", Money.format(youOwe), c.danger)
        val net = owedToYou - youOwe
        Stat("Net", (if (net < 0) "−" else "") + Money.format(abs(net)), c.balanceColor(net))
    }
}

@Composable
private fun Stat(label: String, value: String, color: Color) {
    Column {
        Text(label, style = HubType.small, color = Hub.colors.inkMuted)
        Text(value, style = HubType.section, color = color, maxLines = 1)
    }
}

@Composable
private fun PersonRow(pb: PersonBalance, onClick: () -> Unit) {
    val c = Hub.colors
    val last = pb.lastAt?.let { "Last: " + shortDate(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()) }
    HubListRow(
        icon = Icons.Outlined.Person,
        title = pb.person.name,
        meta = last ?: "No entries yet",
        trailing = LedgerMath.shortText(pb.balancePaise),
        trailingColor = c.balanceColor(pb.balancePaise),
        iconTint = c.balanceColor(pb.balancePaise).takeIf { pb.balancePaise != 0L } ?: c.ink,
        onClick = onClick,
    )
}

/**
 * One person: the balance large, buttons to record money either way and to share a reminder,
 * then the history with the balance after each entry. Tap an entry to edit, swipe to delete.
 */
@Composable
private fun PersonPage(pb: PersonBalance, onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.container
    val c = Hub.colors
    val person = pb.person
    val entriesFlow = remember(person.id) { app.ledger.entries(person.id) }
    // Null until Room answers, so the page doesn't flash "Nothing yet" on every open.
    val loaded by entriesFlow.collectAsStateWithLifecycle<List<LedgerRecord>?>(initialValue = null)
    val entries = loaded.orEmpty()
    val balance = pb.balancePaise
    val running = remember(entries) { LedgerMath.runningBalances(entries) }
    val snackbar = remember { SnackbarHostState() }
    val uiScope = rememberCoroutineScope()

    // The entry sheet: null when closed; otherwise the record (id 0 = new) and whether you paid.
    var editing by rememberSaveable(stateSaver = EditingSaver) { mutableStateOf<Pair<LedgerRecord, Boolean>?>(null) }
    var renaming by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    fun newEntry(youPaid: Boolean) {
        editing = LedgerRecord(personId = person.id, amountPaise = 0L, at = System.currentTimeMillis()) to youPaid
    }
    fun delete(r: LedgerRecord) {
        app.appScope.launch { app.ledger.delete(r) }
        uiScope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val undo = snackbar.showSnackbar(
                "Deleted ${LedgerMath.entryText(person.name, r.amountPaise).lowercase()}",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short,
            )
            if (undo == SnackbarResult.ActionPerformed) app.appScope.launch { app.ledger.restore(r) }
        }
    }
    fun share() {
        val text = LedgerMath.shareMessage(person.name, balance, LedgerMath.openEntries(entries))
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(send, "Send to ${person.name}"))
    }

    ScreenScaffold(
        title = person.name,
        navigationIcon = { BackButton(onBack) },
        snackbarHost = snackbar,
        actions = {
            CircleIconButton(Icons.Outlined.Edit, "Rename ${person.name}", onClick = { renaming = true })
            Spacer(Modifier.size(8.dp))
            CircleIconButton(Icons.Outlined.Delete, "Delete ${person.name}", onClick = { confirmDelete = true })
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = Gutter, end = Gutter, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "hero") {
                Column(
                    hubAnimateItem(fadeIn = false)
                        .fillMaxWidth()
                        .hubCard(if (balance > 0) c.incomeSoft() else if (balance < 0) c.dangerSoft else c.raised)
                        .padding(20.dp),
                ) {
                    Text(LedgerMath.balanceText(person.name, balance), style = HubType.title, color = c.balanceColor(balance).takeIf { balance != 0L } ?: c.ink)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        PrimaryButton("I paid", onClick = { newEntry(youPaid = true) }, modifier = Modifier.weight(1f))
                        PrimaryButton(
                            "${person.name.substringBefore(' ')} paid",
                            onClick = { newEntry(youPaid = false) },
                            modifier = Modifier.weight(1f),
                            container = c.raised2,
                            content = c.ink,
                        )
                    }
                    if (balance != 0L) {
                        QuietButton(
                            if (balance > 0) "Send a reminder" else "Send a note",
                            onClick = { share() },
                            icon = Icons.Outlined.Share,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
            if (entries.isNotEmpty()) {
                item(key = "historyHead") {
                    Text("History", style = HubType.section, color = c.ink, modifier = hubAnimateItem().padding(start = 4.dp, top = 8.dp))
                }
            } else if (loaded != null) {
                item(key = "noHistory") {
                    Text(
                        "Nothing yet. Use “I paid” when you pay or lend, and “${person.name.substringBefore(' ')} paid” when they do.",
                        style = HubType.body, color = c.inkMuted, modifier = hubAnimateItem().padding(4.dp),
                    )
                }
            }
            itemsIndexed(entries, key = { _, r -> r.id }) { i, r ->
                EntryRow(
                    record = r,
                    name = person.name,
                    balanceAfter = running.getOrElse(i) { 0L },
                    onEdit = { editing = r to (r.amountPaise >= 0) },
                    onDelete = { delete(r) },
                    modifier = hubAnimateItem(),
                )
            }
        }
    }

    editing?.let { (r, youPaid) ->
        LedgerEntrySheet(
            name = person.name,
            initial = r,
            youPaid = youPaid,
            onDismiss = { editing = null },
            onSave = { saved ->
                editing = null
                app.appScope.launch { app.ledger.save(saved) }
            },
            onDelete = if (r.id != 0L) ({ editing = null; delete(r) }) else null,
        )
    }
    if (renaming) {
        var takenName by remember { mutableStateOf<String?>(null) }
        NameSheet(
            title = "Rename",
            initial = person.name,
            takenName = takenName,
            onDismiss = { renaming = false },
            onSave = { name ->
                app.appScope.launch {
                    if (app.ledger.rename(person, name)) renaming = false else takenName = name
                }
            },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${person.name}?", style = HubType.section) },
            text = {
                Text(
                    if (balance == 0L) "Their history goes too." else "${LedgerMath.balanceText(person.name, balance)}. Their whole history goes too.",
                    style = HubType.body,
                )
            },
            confirmButton = {
                QuietButton("Delete", color = c.danger, onClick = {
                    confirmDelete = false
                    onBack()
                    app.appScope.launch { app.ledger.deletePerson(person) }
                })
            },
            dismissButton = { QuietButton("Cancel", color = c.inkMuted, onClick = { confirmDelete = false }) },
            containerColor = c.raised,
        )
    }
}

/** One entry: what it was, when, the balance after it, and the amount in its direction's colour. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryRow(
    record: LedgerRecord,
    name: String,
    balanceAfter: Long,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Hub.colors
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(20.dp)
    val date = Instant.ofEpochMilli(record.at).atZone(ZoneId.systemDefault()).toLocalDate()
    val dateText = if (date == LocalDate.now()) "Today" else shortDate(date)
    val what = LedgerMath.entryText(name, record.amountPaise)

    // Not saved state: an entry brought back by Undo must come back settled, not already swiped.
    val density = LocalDensity.current
    val dismiss = remember(density) {
        SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled, density, positionalThreshold = { it * 0.45f })
    }
    val latestDelete by rememberUpdatedState(onDelete)
    LaunchedEffect(dismiss.currentValue) {
        if (dismiss.currentValue == SwipeToDismissBoxValue.EndToStart) latestDelete()
    }
    LaunchedEffect(dismiss.targetValue) {
        if (dismiss.targetValue == SwipeToDismissBoxValue.EndToStart) haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
    }
    var widthPx by remember { mutableIntStateOf(1) }
    val swipe: () -> Float = { runCatching { (-dismiss.requireOffset() / widthPx).coerceIn(0f, 1f) }.getOrDefault(0f) }

    SwipeToDismissBox(
        state = dismiss,
        modifier = modifier.onSizeChanged { widthPx = it.width.coerceAtLeast(1) },
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = (swipe() * 6f).coerceAtMost(1f) }
                    .clip(shape)
                    .background(c.danger),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = null, tint = c.onDanger, modifier = Modifier.padding(end = 24.dp).size(24.dp))
            }
        },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(c.raised)
                .pressable(onClick = onEdit, pressedScale = 0.98f, onClickLabel = "Edit entry")
                .semantics { customActions = listOf(CustomAccessibilityAction("Delete entry") { latestDelete(); true }) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    record.note.ifBlank { what },
                    style = HubType.bodyStrong,
                    color = c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(dateText, what.takeIf { record.note.isNotBlank() }, "then ${LedgerMath.shortText(balanceAfter).replaceFirstChar { it.lowercase() }}")
                        .joinToString(" · "),
                    style = HubType.small.copy(fontSize = 11.sp, lineHeight = 16.sp),
                    color = c.inkMuted,
                    maxLines = 2,
                )
            }
            Text(
                (if (record.amountPaise >= 0) "+" else "−") + Money.format(abs(record.amountPaise)),
                style = HubType.bodyStrong,
                color = c.balanceColor(record.amountPaise),
            )
        }
    }
}
