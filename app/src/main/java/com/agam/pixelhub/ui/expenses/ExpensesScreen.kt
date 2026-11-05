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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
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
import com.agam.pixelhub.data.ExpenseCategory
import com.agam.pixelhub.expenses.CategoryTotal
import com.agam.pixelhub.expenses.Money
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
import com.agam.pixelhub.ui.QuickAction
import com.agam.pixelhub.ui.ScreenScaffold
import com.agam.pixelhub.ui.hubCard
import com.agam.pixelhub.ui.motion.LocalReducedMotion
import com.agam.pixelhub.ui.motion.Motion
import com.agam.pixelhub.ui.motion.RollingText
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

/** A request to open the editor; a fresh object each time so the sheet always starts clean. */
private class EditorRequest(val initial: Expense?)

/** Keeps an open editor (and the expense it started from) through rotation and theme changes. */
private val EditorRequestSaver: Saver<EditorRequest?, ArrayList<Any>> = Saver(
    save = { r ->
        r?.let {
            val e = it.initial
            if (e == null) arrayListOf(false)
            else arrayListOf(true, e.id, e.amountPaise, e.category.name, e.note, e.spentAt)
        }
    },
    restore = { l ->
        if (l[0] == false) EditorRequest(null)
        else EditorRequest(
            Expense(
                id = l[1] as Long, amountPaise = l[2] as Long, category = ExpenseCategory.valueOf(l[3] as String),
                note = l[4] as String, spentAt = l[5] as Long,
            ),
        )
    },
)

private val TopShape = RoundedCornerShape(topStart = CardRadius, topEnd = CardRadius)
private val BottomShape = RoundedCornerShape(bottomStart = CardRadius, bottomEnd = CardRadius)
private val RowShape = RoundedCornerShape(18.dp)
private val MonthPillWidth = 176.dp

/**
 * The Expenses page, laid out like a wallet: the month's spending on a violet card with the
 * month switcher seated in its notch and the biggest category peeking out behind, a card of
 * quick actions, the categories, then the month's transactions in one card per day.
 * Everything is a direct control; the assistant is only another way in.
 */
@Composable
fun ExpensesScreen() {
    val context = LocalContext.current
    val app = context.container
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
    var filter by rememberSaveable { mutableStateOf<ExpenseCategory?>(null) }
    var editor by rememberSaveable(stateSaver = EditorRequestSaver) { mutableStateOf<EditorRequest?>(null) }
    var budgetsOpen by rememberSaveable { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val uiScope = rememberCoroutineScope()

    // The last summary stays up while the next month loads, so nothing flashes empty.
    var summary by remember { mutableStateOf<MonthSummary?>(null) }
    LaunchedEffect(month) { app.expenses.month(month).collect { summary = it } }
    val s = summary

    val list = rememberLazyListState()
    val stagger = rememberStagger()
    val zone = remember { ZoneId.systemDefault() }
    // The floating Add appears once the quick actions have scrolled away.
    val showFab by remember { derivedStateOf { list.firstVisibleItemIndex >= 2 } }

    val days = remember(s, filter) {
        s?.expenses.orEmpty()
            .filter { filter == null || it.category == filter }
            .groupBy { Instant.ofEpochMilli(it.spentAt).atZone(zone).toLocalDate() }
            .toList()
    }

    fun delete(e: Expense) {
        app.appScope.launch { app.expenses.delete(e) }
        uiScope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val what = e.note.ifBlank { e.category.label }
            val undo = snackbar.showSnackbar("Deleted ${Money.format(e.amountPaise)} $what", actionLabel = "Undo", duration = SnackbarDuration.Short)
            if (undo == SnackbarResult.ActionPerformed) app.appScope.launch { app.expenses.restore(e) }
        }
    }
    fun add() {
        editor = EditorRequest(draftFor(month, thisMonth))
    }
    fun repeatLast() {
        val last = s?.expenses?.firstOrNull()
        editor = EditorRequest(
            last?.let {
                Expense(amountPaise = it.amountPaise, category = it.category, note = it.note, spentAt = System.currentTimeMillis())
            } ?: draftFor(month, thisMonth),
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
                    onFilter = { filter = it },
                    modifier = Modifier.animateItem().staggerIn(stagger, "hero", 0).padding(horizontal = Gutter),
                )
            }
            item(key = "actions") {
                QuickActionsCard(
                    filter = filter,
                    hasLast = s?.expenses?.isNotEmpty() == true,
                    onAdd = { add() },
                    onRepeat = { repeatLast() },
                    onFilter = { picking = true },
                    onBudgets = { budgetsOpen = true },
                    modifier = Modifier.animateItem().staggerIn(stagger, "actions", 1),
                )
            }
            if (s == null) return@LazyColumn

            if (s.expenses.isEmpty()) {
                item(key = "empty") {
                    Column(
                        Modifier
                            .animateItem()
                            .staggerIn(stagger, "empty", 2)
                            .fillMaxWidth()
                            .padding(horizontal = Gutter)
                            .padding(top = 16.dp)
                            .hubCard(c.raised)
                            .padding(bottom = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        EmptyState(
                            title = "No spending logged",
                            body = "Tap Add to log one. You can also tell the assistant “Spent 250 on lunch”.",
                            accent = c.dawn,
                        )
                        PrimaryButton("Add expense", onClick = { add() }, icon = Icons.Outlined.Add)
                    }
                }
                return@LazyColumn
            }

            item(key = "cats") {
                CategoriesCard(
                    s = s,
                    filter = filter,
                    onFilter = { filter = it },
                    onBudgets = { budgetsOpen = true },
                    modifier = Modifier.animateItem().staggerIn(stagger, "cats", 2),
                )
            }
            item(key = "txHead") {
                TransactionsHeader(
                    filter = filter,
                    onClear = { filter = null },
                    modifier = Modifier.animateItem().staggerIn(stagger, "txHead", 3),
                )
            }
            if (days.isEmpty()) {
                item(key = "txEmpty") {
                    Text(
                        "Nothing on ${filter?.label?.lowercase() ?: "this"} in ${monthName(s.month, thisMonth)}.",
                        style = HubType.body,
                        color = c.inkMuted,
                        modifier = Modifier.animateItem().padding(horizontal = Gutter, vertical = 8.dp),
                    )
                }
            }
            var n = 4
            days.forEach { (date, dayList) ->
                val dayIndex = n++
                val lastId = dayList.last().id
                item(key = "day-$date") {
                    DayTop(
                        date = date,
                        today = today,
                        totalPaise = dayList.sumOf { it.amountPaise },
                        modifier = Modifier.animateItem().staggerIn(stagger, "day-$date", dayIndex),
                    )
                }
                val base = n
                n += dayList.size
                items(dayList, key = { it.id }) { e ->
                    val at = Instant.ofEpochMilli(e.spentAt).atZone(zone)
                    TransactionRow(
                        e = e,
                        meta = "${Fmt.time(context, at.toLocalTime()).lowercase()} • ${at.format(MetaDate)}",
                        last = e.id == lastId,
                        onEdit = { editor = EditorRequest(e) },
                        onDelete = { delete(e) },
                        modifier = Modifier.animateItem().staggerIn(stagger, e.id, base + dayList.indexOf(e)),
                    )
                }
            }
        }
    }

    editor?.let { req ->
        key(req) {
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
    if (picking) {
        FilterSheet(
            selected = filter,
            spent = s?.byCategory.orEmpty().associate { it.category to it.spentPaise },
            onPick = { cat ->
                filter = cat
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

private val MetaDate: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM", Locale.getDefault())

/** A new expense for a past month starts on that month's last day; this month starts "now". */
private fun draftFor(month: YearMonth, thisMonth: YearMonth): Expense? =
    if (month == thisMonth) {
        null
    } else {
        Expense(
            amountPaise = 0L,
            category = ExpenseCategory.FOOD,
            spentAt = month.atEndOfMonth().atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
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
 * and the budget itself. The month switcher sits in the notch; the sun card behind shows the
 * biggest category and filters to it when tapped.
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
    val on = c.onDawn
    // Small labels need full white on the brighter night violet to stay legible.
    val soft = on.copy(alpha = if (c.isDark) 1f else 0.9f)
    val total = s?.totalPaise ?: 0L
    val budget = s?.budgetPaise?.takeIf { it > 0L }
    val over = budget != null && total > budget
    val top = s?.byCategory?.firstOrNull { it.spentPaise > 0L }
    val name = monthName(s?.month ?: month, thisMonth)
    val totalText = rememberRolledIn(s?.let { Money.format(it.totalPaise) }, delayMs = 200)
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
                    Text(if (over) "Over by" else "Left", style = RowMeta, color = soft, maxLines = 1)
                    RollingText(
                        Money.format(if (over) total - budget else budget - total),
                        style = HubType.clockSmall,
                        color = on,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.padding(end = MonthPillWidth)) {
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

/** Add, repeat the last expense, filter by category, budgets: each an icon in a round well. */
@Composable
private fun QuickActionsCard(
    filter: ExpenseCategory?,
    hasLast: Boolean,
    onAdd: () -> Unit,
    onRepeat: () -> Unit,
    onFilter: () -> Unit,
    onBudgets: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Hub.colors
    val filterTone by animateColorAsState(filter?.color(c) ?: c.well, tween(200), label = "filterTone")
    val filterTint by animateColorAsState(filter?.onColor(c) ?: c.ink, tween(200), label = "filterTint")
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Gutter)
            .padding(top = 16.dp)
            .hubCard(c.raised)
            .padding(horizontal = 6.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        QuickAction(Icons.Outlined.Add, "Add", onClick = onAdd, modifier = Modifier.weight(1f))
        QuickAction(
            Icons.Outlined.Replay,
            "Repeat",
            onClick = onRepeat,
            modifier = Modifier.weight(1f),
            tint = if (hasLast) c.ink else c.inkMuted,
        )
        QuickAction(
            filter?.icon ?: Icons.Outlined.FilterList,
            "Filter",
            onClick = onFilter,
            modifier = Modifier.weight(1f),
            tone = filterTone,
            tint = filterTint,
        )
        QuickAction(Icons.Outlined.Tune, "Budgets", onClick = onBudgets, modifier = Modifier.weight(1f))
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
        CategoryBar(s, Modifier.padding(horizontal = 18.dp), track = c.well, highlight = filter)
        Spacer(Modifier.height(10.dp))
        s.byCategory.forEach { t ->
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
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Transactions
// ---------------------------------------------------------------------------------------------

/** "Transactions", or the filtered category with a "Show all" pill that pops in beside it. */
@Composable
private fun TransactionsHeader(filter: ExpenseCategory?, onClear: () -> Unit, modifier: Modifier = Modifier) {
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
            targetState = filter,
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
        ) { f ->
            Text(
                f?.label ?: "Transactions",
                style = HubType.section,
                color = c.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        AnimatedVisibility(
            visible = filter != null,
            enter = fadeIn(tween(160)) + scaleIn(if (reduced) tween(0) else Motion.snappy(), initialScale = 0.85f),
            exit = fadeOut(tween(120)) + scaleOut(tween(140), targetScale = 0.9f),
        ) {
            Row(
                Modifier
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

/** The top of a day's card: the day, and what it cost, rolling as expenses come and go. */
@Composable
private fun DayTop(date: LocalDate, today: LocalDate, totalPaise: Long, modifier: Modifier = Modifier) {
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
        Text(dayLabel(date, today), style = RowTitle, color = c.ink, modifier = Modifier.weight(1f), maxLines = 1)
        RollingText(Money.format(totalPaise), style = SmallFigures, color = c.inkMuted)
    }
}

/**
 * One expense as a list row inside its day's card: the category in a tinted well, the note
 * (or the category), "9:41 am • 12 November", and the amount. Tap to edit. Swipe left to
 * delete: the danger ground shows through and the bin grows as you drag, with a tick of
 * haptics at the point of no return.
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
    val dismiss = rememberSwipeToDismissBoxState(positionalThreshold = { it * 0.45f })
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
            modifier = Modifier
                .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
                .semantics {
                    customActions = listOf(CustomAccessibilityAction("Delete") { onDelete(); true })
                },
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
            HubListRow(
                icon = e.category.icon,
                title = e.note.ifBlank { e.category.label },
                meta = meta,
                modifier = Modifier.background(c.raised),
                trailing = Money.format(e.amountPaise),
                iconTint = e.category.color(c),
                iconTone = e.category.soft(c),
                onClick = onEdit,
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Filter
// ---------------------------------------------------------------------------------------------

/** Every category as a well with this month's spend under it; tap one to show only it. */
@Composable
private fun FilterSheet(
    selected: ExpenseCategory?,
    spent: Map<ExpenseCategory, Long>,
    onPick: (ExpenseCategory?) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = Hub.colors
    val stagger = rememberStagger()
    val allBg by animateColorAsState(if (selected == null) c.ink else c.raised2, tween(200), label = "allBg")
    val allFg by animateColorAsState(if (selected == null) c.ground else c.ink, tween(200), label = "allFg")
    HubSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Text("Show a category", style = SheetTitle, color = c.ink, modifier = Modifier.staggerIn(stagger, "title", 0))
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier
                    .staggerIn(stagger, "all", 1)
                    .fillMaxWidth()
                    .pressable(onClick = { onPick(null) }, pressedScale = 0.97f, showRipple = false, role = Role.RadioButton)
                    .semantics { this.selected = selected == null }
                    .heightIn(min = 52.dp)
                    .clip(CircleShape)
                    .background(allBg)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("All categories", style = HubType.button, color = allFg, modifier = Modifier.weight(1f))
                if (selected == null) Icon(Icons.Outlined.Check, contentDescription = null, tint = allFg, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(16.dp))
            ExpenseCategory.entries.chunked(3).forEachIndexed { r, row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 6.dp).staggerIn(stagger, "row$r", 2 + r)) {
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
                            Text(Money.format(spent[cat] ?: 0L), style = RowMeta, color = c.inkMuted, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Budgets
// ---------------------------------------------------------------------------------------------

private fun budgetText(paise: Long) = Money.format(paise).removePrefix("₹")

private val BudgetPresets = listOf(5_000_00L, 10_000_00L, 20_000_00L, 30_000_00L)

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
    val stagger = rememberStagger()
    val initialTotal = remember { summary.budgetPaise?.takeIf { it > 0L }?.let { budgetText(it) }.orEmpty() }
    val initialCats = remember {
        summary.byCategory
            .mapNotNull { t -> t.budgetPaise?.takeIf { it > 0L }?.let { t.category to budgetText(it) } }
            .toMap()
    }
    var total by rememberSaveable { mutableStateOf(initialTotal) }
    val cats = remember { mutableStateMapOf<ExpenseCategory, String>().apply { putAll(initialCats) } }

    fun bad(text: String) = text.isNotBlank() && Money.parse(text) == null
    val anyBad = bad(total) || cats.values.any { bad(it) }
    val catSum = cats.values.sumOf { Money.parse(it) ?: 0L }
    val totalLimit = Money.parse(total)
    val sumOver = totalLimit != null && catSum > totalLimit
    val sumColor by animateColorAsState(if (sumOver) c.danger else c.inkMuted, tween(200), label = "sumColor")

    HubSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Text("Monthly budgets", style = SheetTitle, color = c.ink, modifier = Modifier.staggerIn(stagger, "title", 0))
            Spacer(Modifier.height(4.dp))
            Text(
                "You'll get a notification at 80% and at 100%. Leave a field empty for no limit.",
                style = HubType.small,
                color = c.inkMuted,
                modifier = Modifier.staggerIn(stagger, "intro", 1),
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
                modifier = Modifier.staggerIn(stagger, "total", 2),
            )
            FlowRow(
                Modifier.fillMaxWidth().padding(top = 10.dp).staggerIn(stagger, "presets", 3),
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
                Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 12.dp).staggerIn(stagger, "perCat", 4),
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
            ExpenseCategory.entries.forEachIndexed { i, cat ->
                val value = cats[cat].orEmpty()
                BudgetField(
                    value = value,
                    onChange = { cats[cat] = it },
                    label = cat.label,
                    isError = bad(value),
                    leading = { CategoryWell(cat, size = 32.dp) },
                    modifier = Modifier.padding(bottom = 10.dp).staggerIn(stagger, cat, 5 + i),
                )
            }
            PrimaryButton(
                text = "Save budgets",
                enabled = !anyBad,
                onClick = {
                    val changes = buildList<Pair<ExpenseCategory?, Long?>> {
                        if (total.trim() != initialTotal) add(null to Money.parse(total))
                        ExpenseCategory.entries.forEach { cat ->
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
        onValueChange = { v -> onChange(v.filter { it.isDigit() || it == '.' || it == ',' }) },
        label = label,
        placeholder = "No limit",
        isError = isError,
        supportingText = if (isError) "Enter an amount like 5,000" else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        leadingIcon = leading,
        modifier = modifier,
    )
}
