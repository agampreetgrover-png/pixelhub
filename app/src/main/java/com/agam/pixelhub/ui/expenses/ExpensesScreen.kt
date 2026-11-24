@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.agam.pixelhub.ui.expenses

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddCard
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agam.pixelhub.container
import com.agam.pixelhub.data.Expense
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agam.pixelhub.data.ExpenseCategory
import com.agam.pixelhub.data.ExpenseKind
import com.agam.pixelhub.expenses.CategoryTotal
import com.agam.pixelhub.expenses.Money
import com.agam.pixelhub.expenses.MonthReport
import com.agam.pixelhub.expenses.oneLine
import com.agam.pixelhub.expenses.MonthSummary
import com.agam.pixelhub.ui.CardRadius
import com.agam.pixelhub.ui.EmptyState
import com.agam.pixelhub.ui.Fmt
import com.agam.pixelhub.ui.Gutter
import com.agam.pixelhub.ui.HubFab
import com.agam.pixelhub.ui.HubListRow
import com.agam.pixelhub.ui.HubSheet
import com.agam.pixelhub.ui.HubTextField
import com.agam.pixelhub.ui.PrimaryButton
import com.agam.pixelhub.ui.QuietButton
import com.agam.pixelhub.ui.QuickAction
import com.agam.pixelhub.ui.ScreenScaffold
import com.agam.pixelhub.ui.hubCard
import com.agam.pixelhub.ui.motion.LocalReducedMotion
import com.agam.pixelhub.ui.motion.Motion
import com.agam.pixelhub.ui.motion.RollingText
import com.agam.pixelhub.ui.motion.hubAnimateItem
import com.agam.pixelhub.ui.motion.labelSwap
import com.agam.pixelhub.ui.motion.popEnter
import com.agam.pixelhub.ui.motion.popExit
import com.agam.pixelhub.ui.motion.pressable
import com.agam.pixelhub.ui.motion.rememberStagger
import com.agam.pixelhub.ui.motion.staggerIn
import com.agam.pixelhub.ui.theme.Hub
import com.agam.pixelhub.ui.theme.HubType
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * A request to open the editor. Each new request gets a fresh [token] so the sheet starts clean;
 * the token is saved with it, so after rotation the sheet keys on the same value and its fields
 * (all rememberSaveable) come back instead of resetting.
 */
private class EditorRequest(val initial: Expense?, val token: Long = System.nanoTime())

/** Keeps an open editor (and the entry it started from, with its kind) through rotation and theme changes. */
private val EditorRequestSaver: Saver<EditorRequest?, ArrayList<Any>> = Saver(
    save = { r ->
        r?.let {
            val e = it.initial
            if (e == null) arrayListOf(it.token, false)
            else arrayListOf(it.token, true, e.id, e.amountPaise, e.category.name, e.note, e.spentAt, e.kind.name)
        }
    },
    restore = { l ->
        val token = l[0] as Long
        if (l[1] == false) EditorRequest(null, token)
        else EditorRequest(
            Expense(
                id = l[2] as Long, amountPaise = l[3] as Long, category = ExpenseCategory.valueOf(l[4] as String),
                note = l[5] as String, spentAt = l[6] as Long,
                kind = (l.getOrNull(7) as? String)?.let { ExpenseKind.valueOf(it) } ?: ExpenseKind.EXPENSE,
            ),
            token,
        )
    },
)

private val TopShape = RoundedCornerShape(topStart = CardRadius, topEnd = CardRadius)
private val BottomShape = RoundedCornerShape(bottomStart = CardRadius, bottomEnd = CardRadius)
private val RowShape = RoundedCornerShape(18.dp)
private val MonthPillWidth = 176.dp

/**
 * The Expenses page, laid out like a wallet: the month's spending (and money in) on a violet
 * card with the month switcher seated in its notch and the biggest category peeking out
 * behind, a card of quick actions, the categories, then the month's transactions, spending and
 * income together, in one card per day. Everything is a direct control; the assistant is only
 * another way in.
 */
@Composable
fun ExpensesScreen(onOpenPeople: () -> Unit = {}) {
    val context = LocalContext.current
    val app = context.container
    // Who owes whom, for the People row. Separate from the month's money.
    val peopleFlow = remember { app.ledger.balances() }
    val people by peopleFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val c = Hub.colors
    val reduced = LocalReducedMotion.current
    // Today and this month move on if the screen stays open past midnight (checked on every resume).
    var thisMonth by remember { mutableStateOf(YearMonth.now()) }
    var today by remember { mutableStateOf(LocalDate.now()) }
    var month by rememberSaveable { mutableStateOf(thisMonth) }
    LifecycleResumeEffect(Unit) {
        val nowMonth = YearMonth.now()
        if (nowMonth != thisMonth) {
            if (month == thisMonth) month = nowMonth
            thisMonth = nowMonth
        }
        today = LocalDate.now()
        onPauseOrDispose { }
    }
    // Either one spending category, or income only (never both), or neither for everything.
    var filter by rememberSaveable { mutableStateOf<ExpenseCategory?>(null) }
    var incomeOnly by rememberSaveable { mutableStateOf(false) }
    var editor by rememberSaveable(stateSaver = EditorRequestSaver) { mutableStateOf<EditorRequest?>(null) }
    var budgetsOpen by rememberSaveable { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val uiScope = rememberCoroutineScope()

    // The last summary stays up while the next month loads, so nothing flashes empty. The report
    // carries the month's summary, so one live query feeds both the page and the Report card.
    var report by remember { mutableStateOf<MonthReport?>(null) }
    // Keyed on today too: the report reads the date only when Room emits, so a new day re-runs it.
    LaunchedEffect(month, today) { app.expenses.report(month).collect { report = it } }
    var reportOpen by rememberSaveable { mutableStateOf(false) }
    val s = report?.summary
    // Opened from the monthly report notification: go to that month and show its report.
    val pendingReport by app.openReport.collectAsStateWithLifecycle()
    LaunchedEffect(pendingReport) {
        val m = pendingReport ?: return@LaunchedEffect
        if (m <= thisMonth) {
            month = m
            reportOpen = true
        }
        app.openReport.value = null
    }

    val list = rememberLazyListState()
    val stagger = rememberStagger()
    val zone = remember { ZoneId.systemDefault() }
    // The floating Add appears once the quick actions have scrolled away.
    val showFab by remember { derivedStateOf { list.firstVisibleItemIndex >= 2 } }

    val days = remember(s, filter, incomeOnly) {
        s?.entries.orEmpty()
            .filter {
                when {
                    incomeOnly -> it.isIncome
                    filter != null -> !it.isIncome && it.category == filter
                    else -> true
                }
            }
            .groupBy { Instant.ofEpochMilli(it.spentAt).atZone(zone).toLocalDate() }
            .toList()
    }

    fun showCategory(cat: ExpenseCategory?) {
        filter = cat
        incomeOnly = false
    }
    fun showIncome(on: Boolean) {
        incomeOnly = on
        if (on) filter = null
    }
    fun delete(e: Expense) {
        app.appScope.launch { app.expenses.delete(e) }
        uiScope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val what = e.note.ifBlank { e.category.label }
            val amount = if (e.isIncome) incomeText(e.amountPaise) else Money.format(e.amountPaise)
            val undo = snackbar.showSnackbar("Deleted $amount $what", actionLabel = "Undo", duration = SnackbarDuration.Short)
            if (undo == SnackbarResult.ActionPerformed) app.appScope.launch { app.expenses.restore(e) }
        }
    }
    fun add(kind: ExpenseKind = ExpenseKind.EXPENSE) {
        editor = EditorRequest(draftFor(month, thisMonth, kind))
    }
    /** Opens the editor on a copy of the month's latest entry of [kind], dated now. */
    fun repeatLast(kind: ExpenseKind) {
        val last = (if (kind == ExpenseKind.INCOME) s?.incomes else s?.expenses)?.firstOrNull()
        editor = EditorRequest(
            last?.let {
                Expense(amountPaise = it.amountPaise, category = it.category, note = it.note, spentAt = System.currentTimeMillis(), kind = kind)
            } ?: draftFor(month, thisMonth, kind),
        )
    }

    ScreenScaffold(
        title = "Expenses",
        snackbarHost = snackbar,
        floatingActionButton = {
            AnimatedVisibility(
                visible = showFab,
                enter = fadeIn(tween(180)) + scaleIn(tween(if (reduced) 0 else 220, easing = Motion.EaseOut), initialScale = 0.85f),
                exit = fadeOut(tween(120)) + scaleOut(tween(160), targetScale = 0.9f),
            ) {
                HubFab(
                    icon = Icons.Outlined.Add,
                    contentDescription = "Add expense",
                    onClick = { add() },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            state = list,
            contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp),
        ) {
            item(key = "hero") {
                ExpenseHero(
                    s = s,
                    month = month,
                    thisMonth = thisMonth,
                    onMonth = { month = it },
                    filter = filter,
                    onFilter = { showCategory(it) },
                    modifier = hubAnimateItem(fadeIn = false).staggerIn(stagger, "hero", 0).padding(horizontal = Gutter),
                )
            }
            item(key = "actions") {
                QuickActionsCard(
                    filter = filter,
                    incomeOnly = incomeOnly,
                    onAdd = { add() },
                    onRepeat = { repeatLast(ExpenseKind.EXPENSE) },
                    onIncome = { add(ExpenseKind.INCOME) },
                    onRepeatIncome = { repeatLast(ExpenseKind.INCOME) },
                    onFilter = { picking = true },
                    onBudgets = { budgetsOpen = true },
                    onPeople = onOpenPeople,
                    modifier = hubAnimateItem(fadeIn = false).staggerIn(stagger, "actions", 1),
                )
            }
            if (people.isNotEmpty()) {
                item(key = "people") {
                    val owed = people.sumOf { it.balancePaise.coerceAtLeast(0) }
                    val owe = people.sumOf { (-it.balancePaise).coerceAtLeast(0) }
                    Box(
                        hubAnimateItem(fadeIn = false)
                            .staggerIn(stagger, "people", 2)
                            .fillMaxWidth()
                            .padding(horizontal = Gutter)
                            .padding(top = 12.dp)
                            .hubCard(c.raised)
                            .padding(4.dp),
                    ) {
                        HubListRow(
                            icon = Icons.Outlined.People,
                            title = "People",
                            meta = "Owed to you ${Money.format(owed)} · You owe ${Money.format(owe)}",
                            onClick = onOpenPeople,
                        )
                    }
                }
            }
            if (s == null) return@LazyColumn

            if (s.entries.isEmpty()) {
                item(key = "empty") {
                    Column(
                        hubAnimateItem(fadeIn = false)
                            .staggerIn(stagger, "empty", 2)
                            .fillMaxWidth()
                            .padding(horizontal = Gutter)
                            .padding(top = 16.dp)
                            .hubCard(c.raised)
                            .padding(bottom = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        EmptyState(
                            title = "Nothing spent or received in ${monthName(s.month, thisMonth)} yet",
                            body = "Log spending with Add, or money that came in with Income. " +
                                "You can also tell the assistant “Spent 250 on lunch”.",
                            accent = c.dawn,
                        )
                        FlowRow(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            PrimaryButton("Add expense", onClick = { add() }, icon = Icons.Outlined.Add)
                            QuietButton("Add income", onClick = { add(ExpenseKind.INCOME) }, icon = Icons.Outlined.AddCard)
                        }
                    }
                }
                return@LazyColumn
            }

            report?.let { r ->
                item(key = "report") {
                    Box(
                        hubAnimateItem(fadeIn = false)
                            .staggerIn(stagger, "report", 2)
                            .fillMaxWidth()
                            .padding(horizontal = Gutter)
                            .padding(top = 12.dp)
                            .hubCard(c.raised)
                            .padding(4.dp),
                    ) {
                        HubListRow(
                            icon = Icons.Outlined.Insights,
                            title = "${monthName(r.month, thisMonth)} report" + if (r.partial) " so far" else "",
                            meta = r.oneLine(),
                            onClick = { reportOpen = true },
                        )
                    }
                }
            }

            // Categories and budgets are about spending, so a month of only income skips them.
            if (s.byCategory.isNotEmpty()) {
                item(key = "cats") {
                    CategoriesCard(
                        s = s,
                        filter = filter,
                        onFilter = { showCategory(it) },
                        onBudgets = { budgetsOpen = true },
                        modifier = hubAnimateItem(fadeIn = false).staggerIn(stagger, "cats", 2),
                    )
                }
            }
            item(key = "txHead") {
                TransactionsHeader(
                    title = if (incomeOnly) "Income" else filter?.label,
                    onClear = { showCategory(null) },
                    modifier = hubAnimateItem(fadeIn = false).staggerIn(stagger, "txHead", 3),
                )
            }
            if (days.isEmpty()) {
                item(key = "txEmpty") {
                    Column(hubAnimateItem().padding(horizontal = Gutter, vertical = 8.dp)) {
                        Text(
                            if (incomeOnly) {
                                "Nothing received in ${monthName(s.month, thisMonth)}."
                            } else {
                                "Nothing on ${filter?.label?.lowercase() ?: "this"} in ${monthName(s.month, thisMonth)}."
                            },
                            style = HubType.body,
                            color = c.inkMuted,
                        )
                        if (incomeOnly) {
                            QuietButton(
                                "Add income",
                                onClick = { add(ExpenseKind.INCOME) },
                                icon = Icons.Outlined.AddCard,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        }
                    }
                }
            }
            days.forEach { (date, dayList) ->
                val lastId = dayList.last().id
                item(key = "day-$date") {
                    DayTop(
                        date = date,
                        today = today,
                        spentPaise = dayList.filterNot { it.isIncome }.sumOf { it.amountPaise },
                        incomePaise = dayList.filter { it.isIncome }.sumOf { it.amountPaise },
                        modifier = hubAnimateItem(),
                    )
                }
                items(dayList, key = { it.id }) { e ->
                    val at = Instant.ofEpochMilli(e.spentAt).atZone(zone)
                    TransactionRow(
                        e = e,
                        meta = "${Fmt.time(context, at.toLocalTime()).lowercase()} • ${at.format(MetaDate)}",
                        last = e.id == lastId,
                        onEdit = { editor = EditorRequest(e) },
                        onDelete = { delete(e) },
                        modifier = hubAnimateItem(),
                    )
                }
            }
        }
    }

    editor?.let { req ->
        key(req.token) {
            ExpenseEditorSheet(
                initial = req.initial,
                onDismiss = { editor = null },
                onSave = { e ->
                    editor = null
                    // Follow the expense to its month so you see where it landed.
                    val landed = YearMonth.from(Instant.ofEpochMilli(e.spentAt).atZone(ZoneId.systemDefault()))
                    if (landed != month && landed <= YearMonth.now()) month = landed
                    app.appScope.launch { app.expenses.save(e) }
                },
                onDelete = req.initial?.takeIf { it.id != 0L }?.let { e ->
                    {
                        editor = null
                        delete(e)
                    }
                },
            )
        }
    }
    if (budgetsOpen) s?.let { b ->
        BudgetsSheet(
            summary = b,
            onDismiss = { budgetsOpen = false },
            onSave = { changes ->
                budgetsOpen = false
                app.appScope.launch { changes.forEach { (cat, limit) -> app.expenses.setBudget(cat, limit) } }
            },
        )
    }
    // Only once the report for the month on screen has loaded, so it never flashes the month before.
    if (reportOpen) report?.takeIf { it.month == month }?.let { r ->
        ReportSheet(report = r, thisMonth = thisMonth, onDismiss = { reportOpen = false })
    }
    if (picking) {
        FilterSheet(
            selected = filter,
            incomeOnly = incomeOnly,
            spent = s?.byCategory.orEmpty().associate { it.category to it.spentPaise },
            incomePaise = s?.incomePaise ?: 0L,
            onPick = { cat ->
                showCategory(cat)
                picking = false
            },
            onIncome = { on ->
                showIncome(on)
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

private val MetaDate: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM", Locale.getDefault())

/**
 * A new entry for a past month starts on that month's last day; this month starts "now".
 * A new expense this month needs no draft; income does, to carry its kind.
 */
private fun draftFor(month: YearMonth, thisMonth: YearMonth, kind: ExpenseKind = ExpenseKind.EXPENSE): Expense? {
    if (month == thisMonth && kind == ExpenseKind.EXPENSE) return null
    val at = if (month == thisMonth) {
        System.currentTimeMillis()
    } else {
        month.atEndOfMonth().atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
    return Expense(
        amountPaise = 0L,
        category = ExpenseCategory.forKind(kind).first(),
        spentAt = at,
        kind = kind,
    )
}

/** "November", or "November 2025" outside this year. */
private fun monthName(m: YearMonth, thisMonth: YearMonth): String =
    m.format(DateTimeFormatter.ofPattern(if (m.year == thisMonth.year) "MMMM" else "MMMM yyyy", Locale.getDefault()))

// ---------------------------------------------------------------------------------------------
// The hero card
// ---------------------------------------------------------------------------------------------

/**
 * The month on the violet card: what was spent, what's left of the budget (or how far over),
 * and beside the notch either the budget itself or, once money came in, what came in and the
 * net. The month switcher sits in the notch; the sun card behind shows the biggest category
 * and filters to it when tapped.
 */
@Composable
private fun ExpenseHero(
    s: MonthSummary?,
    month: YearMonth,
    thisMonth: YearMonth,
    onMonth: (YearMonth) -> Unit,
    filter: ExpenseCategory?,
    onFilter: (ExpenseCategory?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Hub.colors
    val reduced = LocalReducedMotion.current
    val on = c.onDawn
    // Small labels need full white on the brighter night violet to stay legible.
    val soft = on.copy(alpha = if (c.isDark) 1f else 0.9f)
    val total = s?.totalPaise ?: 0L
    val budget = s?.budgetPaise?.takeIf { it > 0L }
    val over = budget != null && total > budget
    val top = s?.byCategory?.firstOrNull { it.spentPaise > 0L }
    val name = monthName(s?.month ?: month, thisMonth)
    val totalText = rememberRolledIn(s?.let { Money.format(it.totalPaise) }, delayMs = 200, key = "expenses-total")
    val count = s?.expenses?.size ?: 0
    val corner = when {
        s == null -> ""
        budget != null -> "${(total * 100.0 / budget).roundToInt()}% used"
        count == 1 -> "1 expense"
        else -> "$count expenses"
    }
    val showing = top != null && filter == top.category

    NotchedHero(
        color = c.dawn,
        notchWidth = MonthPillWidth,
        modifier = modifier,
        peekColor = c.sun,
        peek = {
            Icon(
                top?.category?.icon ?: Icons.Outlined.Savings,
                contentDescription = null,
                tint = c.onSun,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                when {
                    top == null -> "Nothing spent in $name yet"
                    showing -> "Showing ${top.category.label.lowercase()} only"
                    else -> "Most on ${top.category.label.lowercase()}"
                },
                style = RowTitle,
                color = c.onSun,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (top != null) {
                Spacer(Modifier.width(8.dp))
                Text(Money.format(top.spentPaise), style = HubType.clockSmall.copy(fontSize = 14.sp), color = c.onSun, maxLines = 1)
            }
        },
        onPeek = top?.let { t -> { onFilter(if (filter == t.category) null else t.category) } },
        peekLabel = top?.let { if (showing) "Show all categories" else "Show ${it.category.label} only" },
        pill = { MonthPill(month = month, thisMonth = thisMonth, onMonth = onMonth) },
    ) {
        Row(Modifier.fillMaxWidth().height(HeroPeek), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.AccountBalanceWallet, contentDescription = null, tint = on, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text("Expenses", style = RowTitle, color = on, maxLines = 1)
            Spacer(Modifier.weight(1f))
            RollingText(corner, style = HubType.clockSmall.copy(fontSize = 14.sp), color = on)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("Spent in $name", style = HubType.small, color = soft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                RollingText(
                    totalText,
                    style = if (totalText.length > 8) HubType.clockLarge.copy(fontSize = 32.sp) else HubType.clockLarge,
                    color = on,
                )
            }
            if (budget != null) {
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 12.dp, bottom = 6.dp)) {
                    AnimatedContent(
                        targetState = over,
                        transitionSpec = { labelSwap(reduced) },
                        contentAlignment = Alignment.CenterEnd,
                        label = "heroLeft",
                    ) { o ->
                        Text(if (o) "Over by" else "Left", style = RowMeta, color = soft, maxLines = 1)
                    }
                    RollingText(
                        Money.format(if (over) total - budget else budget - total),
                        style = HubType.clockSmall,
                        color = on,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        // Beside the notch: money in and the net once anything came in this month; until then,
        // the budget as before.
        val income = s?.incomePaise ?: 0L
        val net = s?.netPaise ?: 0L
        AnimatedContent(
            targetState = income > 0L,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
            label = "heroAside",
        ) { showIncome ->
            Column(Modifier.padding(end = MonthPillWidth)) {
                if (showIncome) {
                    HeroStat(label = "In", value = incomeText(income), soft = soft, on = on)
                    HeroStat(label = "Net", value = signedText(net), soft = soft, on = on)
                } else {
                    Text("Monthly budget", style = RowMeta, color = soft, maxLines = 1)
                    Text(
                        budget?.let { Money.format(it) } ?: "Not set",
                        style = BodyStrongFigures,
                        color = on,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private val HeroStatValue = BodyStrongFigures.copy(fontSize = 14.sp, lineHeight = 20.sp)

/** "In  +₹12,000" on one compact line of the hero; two of these fit beside the notch. */
@Composable
private fun HeroStat(label: String, value: String, soft: Color, on: Color) {
    Row(
        Modifier.clearAndSetSemantics { contentDescription = "$label $value" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = RowMeta, color = soft, maxLines = 1, modifier = Modifier.width(34.dp))
        RollingText(value, style = HeroStatValue, color = on)
    }
}

/**
 * ‹ Nov › in the black pill. The name slides the way you moved; tapping it returns to this
 * month. There is no going past this month.
 */
@Composable
private fun RowScope.MonthPill(month: YearMonth, thisMonth: YearMonth, onMonth: (YearMonth) -> Unit) {
    val c = Hub.colors
    val reduced = LocalReducedMotion.current
    PillDisc(
        icon = Icons.Outlined.ChevronLeft,
        description = "Previous month",
        enabled = true,
        onStep = { onMonth(month.minusMonths(1)) },
    )
    Box(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(CircleShape)
            .pressable(
                onClick = { onMonth(thisMonth) },
                enabled = month != thisMonth,
                pressedScale = 0.94f,
                showRipple = false,
                onClickLabel = "Go to this month",
            )
            .semantics { contentDescription = month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())) },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = month,
            transitionSpec = {
                if (reduced) {
                    fadeIn(tween(150)) togetherWith fadeOut(tween(100))
                } else {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(tween(240, easing = Motion.EaseOut)) { w -> dir * w / 2 } + fadeIn(tween(200))) togetherWith
                        (slideOutHorizontally(tween(200, easing = Motion.EaseOut)) { w -> -dir * w / 2 } + fadeOut(tween(120)))
                }
            },
            label = "monthPill",
        ) { m ->
            Text(
                m.format(DateTimeFormatter.ofPattern(if (m.year == thisMonth.year) "MMM" else "MMM ''yy", Locale.getDefault())),
                style = HubType.button.copy(fontFeatureSettings = "tnum"),
                color = c.onChrome,
                maxLines = 1,
            )
        }
    }
    PillDisc(
        icon = Icons.Outlined.ChevronRight,
        description = "Next month",
        enabled = month < thisMonth,
        onStep = { onMonth(month.plusMonths(1)) },
    )
}

// ---------------------------------------------------------------------------------------------
// Quick actions
// ---------------------------------------------------------------------------------------------

/**
 * Add an expense, add income, filter, budgets: each an icon in a round well. Holding Add or
 * Income repeats the month's latest expense or income (dated now); TalkBack offers the same
 * as an action. The Filter well takes on the colour of what it's showing.
 */
@Composable
private fun QuickActionsCard(
    filter: ExpenseCategory?,
    incomeOnly: Boolean,
    onAdd: () -> Unit,
    onRepeat: () -> Unit,
    onIncome: () -> Unit,
    onRepeatIncome: () -> Unit,
    onFilter: () -> Unit,
    onBudgets: () -> Unit,
    onPeople: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Hub.colors
    val filterTone by animateColorAsState(
        when {
            incomeOnly -> c.ok
            filter != null -> filter.color(c)
            else -> c.well
        },
        tween(200),
        label = "filterTone",
    )
    val filterTint by animateColorAsState(
        when {
            incomeOnly -> c.onOk()
            filter != null -> filter.onColor(c)
            else -> c.ink
        },
        tween(200),
        label = "filterTint",
    )
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Gutter)
            .padding(top = 16.dp)
            .hubCard(c.raised)
            .padding(horizontal = 6.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        HoldAction(
            Icons.Outlined.Add,
            "Add",
            onClick = onAdd,
            onHold = onRepeat,
            clickLabel = "Add expense",
            holdLabel = "Repeat last expense",
            modifier = Modifier.weight(1f),
        )
        HoldAction(
            Icons.Outlined.AddCard,
            "Income",
            onClick = onIncome,
            onHold = onRepeatIncome,
            clickLabel = "Add income",
            holdLabel = "Repeat last income",
            modifier = Modifier.weight(1f),
            tone = c.incomeSoft(),
            tint = c.ok,
        )
        QuickAction(
            when {
                incomeOnly -> Icons.Outlined.Savings
                filter != null -> filter.icon
                else -> Icons.Outlined.FilterList
            },
            "Filter",
            onClick = onFilter,
            modifier = Modifier.weight(1f),
            tone = filterTone,
            tint = filterTint,
        )
        QuickAction(Icons.Outlined.Tune, "Budgets", onClick = onBudgets, modifier = Modifier.weight(1f))
        QuickAction(Icons.Outlined.People, "People", onClick = onPeople, modifier = Modifier.weight(1f))
    }
}

/**
 * A quick action (icon in a round well, name under it) that also does something when held.
 * The hold is offered to accessibility services as a named custom action.
 */
@Composable
private fun HoldAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    onHold: () -> Unit,
    clickLabel: String,
    holdLabel: String,
    modifier: Modifier = Modifier,
    tone: Color = Hub.colors.well,
    tint: Color = Hub.colors.ink,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .pressable(onClick = onClick, onLongClick = onHold, pressedScale = 0.94f, showRipple = false, onClickLabel = clickLabel)
            .semantics { customActions = listOf(CustomAccessibilityAction(holdLabel) { onHold(); true }) }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(54.dp).clip(CircleShape).background(tone), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, style = HubType.small.copy(fontWeight = FontWeight.Medium), color = Hub.colors.ink, maxLines = 1)
    }
}

// ---------------------------------------------------------------------------------------------
// Categories
// ---------------------------------------------------------------------------------------------

/** The month shared out by category: one bar, then a row per category. Rows filter the list. */
@Composable
private fun CategoriesCard(
    s: MonthSummary,
    filter: ExpenseCategory?,
    onFilter: (ExpenseCategory?) -> Unit,
    onBudgets: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Hub.colors
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Gutter)
            .padding(top = 16.dp)
            .hubCard(c.raised)
            .padding(top = 10.dp, bottom = 8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Categories", style = HubType.section, color = c.ink, modifier = Modifier.weight(1f))
            Text(
                "Budgets",
                style = HubType.small.copy(fontWeight = FontWeight.Medium),
                color = c.inkMuted,
                modifier = Modifier
                    .clip(CircleShape)
                    .pressable(onClick = onBudgets, onClickLabel = "Set budgets")
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 10.dp, vertical = 11.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        CategoryBar(s, Modifier.padding(horizontal = 18.dp), track = c.well, highlight = filter, drawKey = "expenses-bar")
        Spacer(Modifier.height(10.dp))
        s.byCategory.forEach { t ->
            // Keyed, so each row's bar and colours stay with its category when the rows re-sort.
            key(t.category) {
                CategoryRow(
                    t = t,
                    total = s.totalPaise,
                    selected = filter == t.category,
                    dimmed = filter != null && filter != t.category,
                    onClick = { onFilter(if (filter == t.category) null else t.category) },
                )
            }
        }
    }
}

/**
 * A category: its well, name, amount and one quiet line, with a budget bar when it has a
 * budget. Tapping filters the transactions; the selected row sits on a well and the rest dim.
 */
@Composable
private fun CategoryRow(
    t: CategoryTotal,
    total: Long,
    selected: Boolean,
    dimmed: Boolean,
    onClick: () -> Unit,
) {
    val c = Hub.colors
    val bg by animateColorAsState(if (selected) c.well else c.raised, tween(200), label = "catBg")
    val contentAlpha by animateFloatAsState(if (dimmed) 0.45f else 1f, tween(200), label = "catAlpha")
    val budget = t.budgetPaise?.takeIf { it > 0L }
    val over = budget != null && t.spentPaise > budget
    val caption = when {
        budget == null -> {
            val pct = if (total > 0L) (t.spentPaise * 100.0 / total).roundToInt() else 0
            if (pct == 0 && t.spentPaise > 0L) "Under 1% of spending" else "$pct% of spending"
        }
        over -> "${Money.format(t.spentPaise - budget)} over ${Money.format(budget)}"
        t.spentPaise == 0L -> "${Money.format(budget)} to spend"
        else -> "${Money.format(budget - t.spentPaise)} left of ${Money.format(budget)}"
    }
    val captionColor by animateColorAsState(if (over) c.danger else c.inkMuted, tween(200), label = "catCaption")

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .pressable(
                onClick = onClick,
                pressedScale = 0.98f,
                showRipple = false,
                onClickLabel = if (selected) "Show all transactions" else "Show ${t.category.label} only",
            )
            .semantics { this.selected = selected }
            .clip(RowShape)
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .graphicsLayer { alpha = contentAlpha },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryWell(t.category, selected = selected)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    t.category.label,
                    style = RowTitle,
                    color = c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                RollingText(Money.format(t.spentPaise), style = HubType.clockSmall.copy(fontSize = 14.sp), color = c.ink)
            }
            Text(caption, style = RowMeta, color = captionColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (budget != null) {
                Spacer(Modifier.height(8.dp))
                ProgressTrack(
                    fraction = t.spentPaise.toFloat() / budget,
                    color = if (over) c.danger else t.category.color(c),
                    track = if (selected) c.raised else c.well,
                    drawKey = "budget-${t.category}",
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Transactions
// ---------------------------------------------------------------------------------------------

/**
 * "Transactions", or what the list is filtered to ([title]: a category, or "Income") with a
 * "Show all" pill that pops in beside it.
 */
@Composable
private fun TransactionsHeader(title: String?, onClear: () -> Unit, modifier: Modifier = Modifier) {
    val c = Hub.colors
    val reduced = LocalReducedMotion.current
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = Gutter, end = Gutter, top = 24.dp, bottom = 0.dp)
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(
            targetState = title,
            transitionSpec = {
                if (reduced) {
                    fadeIn(tween(150)) togetherWith fadeOut(tween(100)) using SizeTransform(clip = false)
                } else {
                    (fadeIn(tween(200, delayMillis = 40)) + slideInVertically(tween(260, easing = Motion.EaseOut)) { it / 2 }) togetherWith
                        (fadeOut(tween(120)) + slideOutVertically(tween(180)) { -it / 2 }) using SizeTransform(clip = false)
                }
            },
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier.weight(1f),
            label = "txTitle",
        ) { t ->
            Text(
                t ?: "Transactions",
                style = HubType.section,
                color = c.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        AnimatedVisibility(
            visible = title != null,
            enter = popEnter(reduced),
            exit = popExit(),
        ) {
            Row(
                Modifier
                    .minimumInteractiveComponentSize()
                    .pressable(onClick = onClear, pressedScale = 0.93f, showRipple = false, onClickLabel = "Show all transactions")
                    .heightIn(min = 40.dp)
                    .clip(CircleShape)
                    .background(c.raised)
                    .padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Close, contentDescription = null, tint = c.ink, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Show all", style = HubType.button, color = c.ink)
            }
        }
    }
}

private fun dayLabel(d: LocalDate, today: LocalDate): String = when (d) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> d.format(
        DateTimeFormatter.ofPattern(if (d.year == today.year) "EEEE, d MMMM" else "EEE d MMM yyyy", Locale.getDefault()),
    )
}

/**
 * The top of a day's card: the day, what it cost and (when any came in) "+₹X" received, each
 * rolling as entries come and go.
 */
@Composable
private fun DayTop(date: LocalDate, today: LocalDate, spentPaise: Long, incomePaise: Long, modifier: Modifier = Modifier) {
    val c = Hub.colors
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Gutter)
            .padding(top = 12.dp)
            .clip(TopShape)
            .background(c.raised)
            .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            dayLabel(date, today),
            style = RowTitle,
            color = c.ink,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (spentPaise > 0L) {
            RollingText(Money.format(spentPaise), style = SmallFigures, color = c.inkMuted)
        }
        if (incomePaise > 0L) {
            if (spentPaise > 0L) Spacer(Modifier.width(10.dp))
            RollingText(incomeText(incomePaise), style = SmallFigures, color = c.ok)
        }
    }
}

/**
 * One entry as a list row inside its day's card: the category in a tinted well, the note
 * (or the category), "9:41 am • 12 November", and the amount. Income reads "+₹5,000" in the
 * ok green on a green well. Tap to edit. Swipe left to delete: the danger ground shows through
 * and the bin grows as you drag, with a tick of haptics at the point of no return.
 */
@Composable
private fun TransactionRow(
    e: Expense,
    meta: String,
    last: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Hub.colors
    val haptics = LocalHapticFeedback.current
    // Not saved state: a deleted row brought back by Undo reuses its key, and saved state would
    // come back already swiped and delete it again. A row always starts settled.
    val density = LocalDensity.current
    val dismiss = remember(density) {
        SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled, density, positionalThreshold = { it * 0.45f })
    }
    val latestDelete by rememberUpdatedState(onDelete)
    LaunchedEffect(dismiss.currentValue) {
        if (dismiss.currentValue == SwipeToDismissBoxValue.EndToStart) latestDelete()
    }
    LaunchedEffect(dismiss.targetValue) {
        if (dismiss.targetValue == SwipeToDismissBoxValue.EndToStart) {
            haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
        }
    }
    var widthPx by remember { mutableIntStateOf(1) }
    // 0 at rest, 1 when the row has travelled its full width. Read only while drawing.
    val swipe: () -> Float = {
        runCatching { (-dismiss.requireOffset() / widthPx).coerceIn(0f, 1f) }.getOrDefault(0f)
    }

    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Gutter)
            .clip(if (last) BottomShape else RectangleShape)
            .background(c.raised)
            .padding(start = 6.dp, end = 6.dp, bottom = if (last) 10.dp else 0.dp),
    ) {
        SwipeToDismissBox(
            state = dismiss,
            modifier = Modifier.onSizeChanged { widthPx = it.width.coerceAtLeast(1) },
            enableDismissFromStartToEnd = false,
            backgroundContent = {
                Box(
                    Modifier
                        .fillMaxSize()
                        // Invisible at rest so it never peeks round the row's corners.
                        .graphicsLayer { alpha = (swipe() * 6f).coerceAtMost(1f) }
                        .clip(RowShape)
                        .background(c.danger),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = null,
                        tint = c.onDanger,
                        modifier = Modifier
                            .padding(end = 24.dp)
                            .size(24.dp)
                            .graphicsLayer {
                                val f = (swipe() / 0.45f).coerceIn(0f, 1f)
                                val s = 0.7f + 0.6f * f
                                scaleX = s
                                scaleY = s
                                rotationZ = -14f * (1f - f)
                            },
                    )
                }
            },
        ) {
            val income = e.isIncome
            HubListRow(
                icon = e.category.icon,
                title = e.note.ifBlank {
                    if (income && e.category == ExpenseCategory.OTHER) "Other income" else e.category.label
                },
                meta = meta,
                // On the row itself, the node TalkBack focuses: on the swipe box above it the
                // action was never reachable, and swiping doesn't work under TalkBack.
                modifier = Modifier
                    .background(c.raised)
                    .semantics {
                        customActions = listOf(CustomAccessibilityAction("Delete") { latestDelete(); true })
                    },
                trailing = if (income) incomeText(e.amountPaise) else Money.format(e.amountPaise),
                trailingColor = if (income) c.ok else c.ink,
                iconTint = if (income) c.ok else e.category.color(c),
                iconTone = if (income) c.incomeSoft() else e.category.soft(c),
                onClick = onEdit,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Filter
// ---------------------------------------------------------------------------------------------

/**
 * Everything, income only, or one spending category (a well with this month's spend under
 * it); tap one to show only it.
 */
@Composable
private fun FilterSheet(
    selected: ExpenseCategory?,
    incomeOnly: Boolean,
    spent: Map<ExpenseCategory, Long>,
    incomePaise: Long,
    onPick: (ExpenseCategory?) -> Unit,
    onIncome: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Hub.colors
    val all = selected == null && !incomeOnly
    HubSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Text("Show transactions", style = SheetTitle, color = c.ink)
            Spacer(Modifier.height(16.dp))
            FilterPill(
                text = "Everything",
                selected = all,
                onClick = { onPick(null) },
            )
            Spacer(Modifier.height(8.dp))
            FilterPill(
                text = "Income only",
                selected = incomeOnly,
                onClick = { onIncome(!incomeOnly) },
                icon = Icons.Outlined.Savings,
                iconTint = c.ok,
                amount = incomeText(incomePaise),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Spending",
                style = HubType.section,
                color = c.ink,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            ExpenseCategory.forKind(ExpenseKind.EXPENSE).chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                    row.forEach { cat ->
                        val isSelected = cat == selected
                        Column(
                            Modifier
                                .weight(1f)
                                .pressable(
                                    onClick = { onPick(if (isSelected) null else cat) },
                                    pressedScale = 0.94f,
                                    showRipple = false,
                                    role = Role.RadioButton,
                                    onClickLabel = cat.label,
                                )
                                .semantics { this.selected = isSelected }
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            CategoryWell(cat, size = 54.dp, selected = isSelected)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                cat.shortLabel,
                                style = HubType.small.copy(fontWeight = FontWeight.Medium),
                                color = c.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            // Ellipsis, not a silent cut: a clipped "₹1,23,4" reads as a different amount.
                            Text(
                                Money.format(spent[cat] ?: 0L), style = RowMeta, color = c.inkMuted,
                                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A whole-width choice in the filter sheet; the selected one fills with ink and shows a tick. */
@Composable
private fun FilterPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = Hub.colors.ink,
    amount: String? = null,
) {
    val c = Hub.colors
    val bg by animateColorAsState(if (selected) c.ink else c.raised2, tween(200), label = "filterPillBg")
    val fg by animateColorAsState(if (selected) c.ground else c.ink, tween(200), label = "filterPillFg")
    // The ok green doesn't hold contrast on an ink fill, so a selected icon takes the label colour.
    val tint by animateColorAsState(if (selected) c.ground else iconTint, tween(200), label = "filterPillTint")
    Row(
        modifier
            .fillMaxWidth()
            .pressable(onClick = onClick, pressedScale = 0.97f, showRipple = false, role = Role.RadioButton)
            .semantics { this.selected = selected }
            .heightIn(min = 52.dp)
            .clip(CircleShape)
            .background(bg)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = HubType.button, color = fg, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (amount != null) {
            Spacer(Modifier.width(8.dp))
            Text(amount, style = HubType.button.copy(fontFeatureSettings = "tnum"), color = fg, maxLines = 1)
        }
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Outlined.Check, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Budgets
// ---------------------------------------------------------------------------------------------

private fun budgetText(paise: Long) = Money.format(paise).removePrefix("₹")

private val BudgetPresets = listOf(5_000_00L, 10_000_00L, 20_000_00L, 30_000_00L)

/** Budgets only measure spending, so only spending categories get one. */
private val SpendingCategories = ExpenseCategory.forKind(ExpenseKind.EXPENSE)

/**
 * Monthly limits: one for everything (with a few one-tap amounts) and one per category.
 * Empty means no limit. Only the fields you changed are saved.
 */
@Composable
private fun BudgetsSheet(
    summary: MonthSummary,
    onDismiss: () -> Unit,
    onSave: (List<Pair<ExpenseCategory?, Long?>>) -> Unit,
) {
    val c = Hub.colors
    val initialTotal = remember { summary.budgetPaise?.takeIf { it > 0L }?.let { budgetText(it) }.orEmpty() }
    val initialCats = remember {
        summary.byCategory
            .mapNotNull { t -> t.budgetPaise?.takeIf { it > 0L }?.let { t.category to budgetText(it) } }
            .toMap()
    }
    var total by rememberSaveable { mutableStateOf(initialTotal) }
    // Saveable, so typed per-category limits survive rotation like the total does.
    val cats = rememberSaveable(saver = CategoryTextSaver) {
        mutableStateMapOf<ExpenseCategory, String>().apply { putAll(initialCats) }
    }

    fun bad(text: String) = text.isNotBlank() && Money.parse(text) == null
    val anyBad = bad(total) || cats.values.any { bad(it) }
    val catSum = cats.values.sumOf { Money.parse(it) ?: 0L }
    val totalLimit = Money.parse(total)
    val sumOver = totalLimit != null && catSum > totalLimit
    val sumColor by animateColorAsState(if (sumOver) c.danger else c.inkMuted, tween(200), label = "sumColor")
    // Any limit typed but not saved (the same test Save uses to pick what changed): dismissing asks first.
    val dirty = total.trim() != initialTotal ||
        SpendingCategories.any { cats[it].orEmpty().trim() != initialCats[it].orEmpty() }

    HubSheet(onDismiss = onDismiss, dirty = dirty) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Text("Monthly budgets", style = SheetTitle, color = c.ink)
            Spacer(Modifier.height(4.dp))
            Text(
                "You'll get a notification at 80% and at 100%. Leave a field empty for no limit.",
                style = HubType.small,
                color = c.inkMuted,
            )
            Spacer(Modifier.height(18.dp))
            BudgetField(
                value = total,
                onChange = { total = it },
                label = "Everything",
                isError = bad(total),
                leading = {
                    Box(Modifier.size(32.dp).clip(CircleShape).background(c.dawn), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.AccountBalanceWallet, contentDescription = null, tint = c.onDawn, modifier = Modifier.size(16.dp))
                    }
                },
            )
            FlowRow(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BudgetPresets.forEach { p ->
                    PresetChip(
                        text = Money.format(p),
                        selected = totalLimit == p,
                        onClick = { total = budgetText(p) },
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Per category", style = HubType.section, color = c.ink, modifier = Modifier.weight(1f))
                AnimatedVisibility(visible = catSum > 0L, enter = fadeIn(tween(200)), exit = fadeOut(tween(140))) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RollingText(Money.format(catSum), style = SmallFigures, color = sumColor)
                        Text(if (sumOver) " is more than the total" else " in all", style = HubType.small, color = sumColor)
                    }
                }
            }
            SpendingCategories.forEach { cat ->
                val value = cats[cat].orEmpty()
                BudgetField(
                    value = value,
                    onChange = { cats[cat] = it },
                    label = cat.label,
                    isError = bad(value),
                    leading = { CategoryWell(cat, size = 32.dp) },
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
            PrimaryButton(
                text = "Save budgets",
                enabled = !anyBad,
                onClick = {
                    val changes = buildList<Pair<ExpenseCategory?, Long?>> {
                        if (total.trim() != initialTotal) add(null to Money.parse(total))
                        SpendingCategories.forEach { cat ->
                            val now = cats[cat].orEmpty().trim()
                            if (now != initialCats[cat].orEmpty()) add(cat to Money.parse(now))
                        }
                    }
                    onSave(changes)
                },
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
            )
        }
    }
}

/** A one-tap amount: a pill on raised2, filled with ink when it matches. */
@Composable
private fun PresetChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = Hub.colors
    val bg by animateColorAsState(if (selected) c.ink else c.raised2, tween(180), label = "presetBg")
    val fg by animateColorAsState(if (selected) c.ground else c.ink, tween(180), label = "presetFg")
    Box(
        Modifier
            .minimumInteractiveComponentSize()
            .pressable(onClick = onClick, pressedScale = 0.93f, showRipple = false, role = Role.RadioButton)
            .semantics { this.selected = selected }
            .heightIn(min = 40.dp)
            .clip(CircleShape)
            .background(bg)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = HubType.button.copy(fontFeatureSettings = "tnum"), color = fg)
    }
}

@Composable
private fun BudgetField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    isError: Boolean,
    leading: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    HubTextField(
        value = value,
        onValueChange = { v ->
            // A pasted "Rs. 50" loses its prefix first, or its dot would leave ".50".
            val kept = Money.stripCurrencyPrefix(v).filter { it.isDigit() || it == '.' || it == ',' }
            // Up to 12 digits, like the People amounts: far past any real budget, and the threshold maths can't overflow.
            if (kept.count(Char::isDigit) <= 12) onChange(kept)
        },
        label = label,
        placeholder = "No limit",
        isError = isError,
        supportingText = if (isError) "Enter an amount like 5,000" else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        leadingIcon = leading,
        modifier = modifier,
    )
}

/** Saves the budgets sheet's per-category text as "NAME=text" strings. */
private val CategoryTextSaver: Saver<SnapshotStateMap<ExpenseCategory, String>, ArrayList<String>> = Saver(
    save = { m -> ArrayList(m.map { (k, v) -> "${k.name}=$v" }) },
    restore = { l ->
        mutableStateMapOf<ExpenseCategory, String>().apply {
            l.forEach { s ->
                val i = s.indexOf('=')
                runCatching { ExpenseCategory.valueOf(s.substring(0, i)) }.getOrNull()?.let { put(it, s.substring(i + 1)) }
            }
        }
    },
)
