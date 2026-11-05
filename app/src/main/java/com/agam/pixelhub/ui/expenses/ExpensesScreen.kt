@file:OptIn(ExperimentalMaterial3Api::class)

package com.agam.pixelhub.ui.expenses

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agam.pixelhub.container
import com.agam.pixelhub.data.Expense
import com.agam.pixelhub.data.ExpenseCategory
import com.agam.pixelhub.expenses.CategoryTotal
import com.agam.pixelhub.expenses.Money
import com.agam.pixelhub.expenses.MonthSummary
import com.agam.pixelhub.ui.EmptyState
import com.agam.pixelhub.ui.Fmt
import com.agam.pixelhub.ui.Gutter
import com.agam.pixelhub.ui.HubFab
import com.agam.pixelhub.ui.HubSheet
import com.agam.pixelhub.ui.HubTextField
import com.agam.pixelhub.ui.PrimaryButton
import com.agam.pixelhub.ui.QuietButton
import com.agam.pixelhub.ui.ScreenScaffold
import com.agam.pixelhub.ui.motion.LocalReducedMotion
import com.agam.pixelhub.ui.motion.Motion
import com.agam.pixelhub.ui.motion.RollingText
import com.agam.pixelhub.ui.motion.pressable
import com.agam.pixelhub.ui.motion.rememberStagger
import com.agam.pixelhub.ui.motion.staggerIn
import com.agam.pixelhub.ui.theme.Hub
import com.agam.pixelhub.ui.theme.HubType
import kotlinx.coroutines.delay
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

/** Remembers that the dial has made its entrance, so scrolling back to it doesn't replay it. */
private class Once { var done = false }

private val RowShape = RoundedCornerShape(24.dp)

/**
 * The Expenses page: a month at a time. A spend dial at the top in the language of the Home
 * dial, the categories as a legend you can tap to filter, then the month's transactions by day.
 */
@Composable
fun ExpensesScreen(onBack: () -> Unit) {
    val app = LocalContext.current.container
    val c = Hub.colors
    val reduced = LocalReducedMotion.current
    val thisMonth = remember { YearMonth.now() }
    var month by rememberSaveable { mutableStateOf(thisMonth) }
    var editor by remember { mutableStateOf<EditorRequest?>(null) }
    var budgetsFor by remember { mutableStateOf<MonthSummary?>(null) }

    fun delete(e: Expense) {
        app.appScope.launch { app.expenses.delete(e) }
    }

    ScreenScaffold(
        title = "Expenses",
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        },
        floatingActionButton = {
            HubFab(
                icon = Icons.Outlined.Add,
                contentDescription = "Add expense",
                onClick = { editor = EditorRequest(draftFor(month, thisMonth)) },
                container = c.ink,
                content = c.ground,
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            MonthSwitcher(month = month, thisMonth = thisMonth, onChange = { month = it })
            AnimatedContent(
                targetState = month,
                transitionSpec = { monthSlide(forward = targetState > initialState, reduced = reduced, fraction = 0.3f) },
                modifier = Modifier.fillMaxWidth().weight(1f),
                label = "monthPage",
            ) { m ->
                MonthPage(
                    month = m,
                    onEdit = { editor = EditorRequest(it) },
                    onDelete = { delete(it) },
                    onBudgets = { budgetsFor = it },
                )
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
                    if (landed != month && landed <= thisMonth) month = landed
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
    budgetsFor?.let { s ->
        BudgetsSheet(
            summary = s,
            onDismiss = { budgetsFor = null },
            onSave = { changes ->
                budgetsFor = null
                app.appScope.launch { changes.forEach { (cat, limit) -> app.expenses.setBudget(cat, limit) } }
            },
        )
    }
}

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

/** Later months arrive from the right, earlier ones from the left. */
private fun monthSlide(forward: Boolean, reduced: Boolean, fraction: Float): ContentTransform =
    if (reduced) {
        fadeIn(tween(180)) togetherWith fadeOut(tween(120))
    } else {
        val dir = if (forward) 1 else -1
        (slideInHorizontally(tween(480, easing = Motion.EaseOut)) { w -> (dir * w * fraction).toInt() } +
            fadeIn(tween(320, delayMillis = 60))) togetherWith
            (slideOutHorizontally(tween(320, easing = Motion.EaseOut)) { w -> (-dir * w * fraction).toInt() } +
                fadeOut(tween(160)))
    }

private fun monthLabel(m: YearMonth): String =
    m.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))

/** ‹ November 2026 ›. The name slides the same way as the page; tapping it returns to this month. */
@Composable
private fun MonthSwitcher(month: YearMonth, thisMonth: YearMonth, onChange: (YearMonth) -> Unit) {
    val c = Hub.colors
    val reduced = LocalReducedMotion.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Gutter - 12.dp).padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundIconButton(
            icon = Icons.Outlined.ChevronLeft,
            description = "Previous month",
            onClick = { onChange(month.minusMonths(1)) },
        )
        AnimatedContent(
            targetState = month,
            transitionSpec = {
                monthSlide(forward = targetState > initialState, reduced = reduced, fraction = 0.6f) using
                    SizeTransform(clip = false)
            },
            contentAlignment = Alignment.Center,
            modifier = Modifier.weight(1f),
            label = "monthLabel",
        ) { m ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(CircleShape)
                    .pressable(
                        onClick = { onChange(thisMonth) },
                        enabled = m != thisMonth,
                        pressedScale = 0.96f,
                        showRipple = false,
                        onClickLabel = "Go to this month",
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(monthLabel(m), style = HubType.section.copy(fontFeatureSettings = "tnum"), color = c.ink, textAlign = TextAlign.Center, maxLines = 1)
            }
        }
        RoundIconButton(
            icon = Icons.Outlined.ChevronRight,
            description = "Next month",
            enabled = month < thisMonth,
            onClick = { onChange(month.plusMonths(1)) },
        )
    }
}

@Composable
private fun RoundIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val c = Hub.colors
    val tint by animateColorAsState(if (enabled) c.ink else c.inkMuted.copy(alpha = 0.3f), tween(200), label = "chevron")
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .pressable(onClick = onClick, enabled = enabled, pressedScale = 0.88f, onClickLabel = description)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint)
    }
}

/** One month: the dial, the categories and the transactions, all from one live summary. */
@Composable
private fun MonthPage(
    month: YearMonth,
    onEdit: (Expense) -> Unit,
    onDelete: (Expense) -> Unit,
    onBudgets: (MonthSummary) -> Unit,
) {
    val context = LocalContext.current
    val app = context.container
    val c = Hub.colors
    val flow = remember(month) { app.expenses.month(month) }
    // Null until the database answers, so the empty state never flashes.
    val loaded by flow.collectAsStateWithLifecycle<MonthSummary?>(initialValue = null)
    var filter by remember { mutableStateOf<ExpenseCategory?>(null) }
    val stagger = rememberStagger()
    // Bars that have already grown in; scrolling back doesn't replay them.
    val grown = remember { mutableSetOf<ExpenseCategory>() }
    val dialIntro = remember { Once() }
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now() }
    val s = loaded

    val days = remember(s, filter) {
        s?.expenses.orEmpty()
            .filter { filter == null || it.category == filter }
            .groupBy { Instant.ofEpochMilli(it.spentAt).atZone(zone).toLocalDate() }
            .toList()
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = 136.dp),
    ) {
        if (s == null) return@LazyColumn

        if (s.expenses.isEmpty()) {
            item(key = "empty") {
                Column(
                    Modifier.fillMaxWidth().animateItem().staggerIn(stagger, "empty", 0),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    EmptyState(
                        title = "No spending logged",
                        body = "Tap + to add an expense, or tell the assistant “Spent 250 on lunch”.",
                        accent = c.ink,
                    )
                    QuietButton("Set budgets", onClick = { onBudgets(s) }, icon = Icons.Outlined.Tune)
                }
            }
            return@LazyColumn
        }

        item(key = "dial") {
            SpendDial(
                s = s,
                filter = filter,
                intro = !dialIntro.done,
                onIntro = { dialIntro.done = true },
                modifier = Modifier.animateItem().staggerIn(stagger, "dial", 0),
            )
        }

        item(key = "catHead") {
            Row(
                Modifier
                    .animateItem()
                    .fillMaxWidth()
                    .padding(start = Gutter, end = Gutter - 4.dp, top = 24.dp, bottom = 6.dp)
                    .staggerIn(stagger, "catHead", 1),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Categories", style = HubType.section, color = c.ink, modifier = Modifier.weight(1f))
                QuietButton("Set budgets", onClick = { onBudgets(s) }, icon = Icons.Outlined.Tune)
            }
        }
        itemsIndexed(s.byCategory, key = { _, t -> "cat-${t.category.name}" }) { i, t ->
            CategoryRow(
                t = t,
                total = s.totalPaise,
                selected = filter == t.category,
                dimmed = filter != null && filter != t.category,
                index = i,
                grow = t.category !in grown,
                onGrown = { grown += t.category },
                onClick = { filter = if (filter == t.category) null else t.category },
                modifier = Modifier.animateItem().staggerIn(stagger, "cat-${t.category.name}", 2 + i),
            )
        }

        item(key = "txHead") {
            TransactionsHeader(
                filter = filter,
                onClear = { filter = null },
                modifier = Modifier.animateItem().staggerIn(stagger, "txHead", 3 + s.byCategory.size),
            )
        }
        if (days.isEmpty()) {
            item(key = "txEmpty") {
                Text(
                    "Nothing on ${filter?.label?.lowercase() ?: "this"} yet this month.",
                    style = HubType.body,
                    color = c.inkMuted,
                    modifier = Modifier.animateItem().padding(horizontal = Gutter, vertical = 8.dp),
                )
            }
        }
        var n = 4 + s.byCategory.size
        days.forEach { (date, list) ->
            val dayIndex = n++
            item(key = "day-$date") {
                DayHeader(
                    date = date,
                    today = today,
                    totalPaise = list.sumOf { it.amountPaise },
                    modifier = Modifier.animateItem().staggerIn(stagger, "day-$date", dayIndex),
                )
            }
            val base = n
            n += list.size
            items(list, key = { it.id }) { e ->
                TransactionRow(
                    e = e,
                    timeText = Fmt.time(context, Instant.ofEpochMilli(e.spentAt).atZone(zone).toLocalTime()),
                    onEdit = { onEdit(e) },
                    onDelete = { onDelete(e) },
                    modifier = Modifier.animateItem().staggerIn(stagger, e.id, base + list.indexOf(e)),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// The spend dial
// ---------------------------------------------------------------------------------------------

/**
 * The hero. The ring sweeps in segment by segment; in the middle the total rolls up from
 * zeros. Filtering by a category dims the other segments and the centre shows that
 * category instead. Over budget, the line under the total turns to danger.
 */
@Composable
private fun SpendDial(
    s: MonthSummary,
    filter: ExpenseCategory?,
    intro: Boolean,
    onIntro: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Hub.colors
    val budget = s.budgetPaise?.takeIf { it > 0L }
    val catTotal = filter?.let { f -> s.byCategory.firstOrNull { it.category == f } }
    val shown = if (filter != null) catTotal?.spentPaise ?: 0L else s.totalPaise
    val limit = if (filter != null) catTotal?.budgetPaise?.takeIf { it > 0L } else budget
    val over = limit != null && shown > limit

    val line1 = when {
        filter != null -> "on ${filter.label.lowercase()}"
        budget != null -> "of ${Money.format(budget)} budget"
        else -> "this month"
    }
    val line2 = limit?.let { l ->
        if (shown > l) "${Money.format(shown - l)} over" else "${Money.format(l - shown)} left"
    }
    val line2Color by animateColorAsState(if (over) c.danger else c.ink, tween(260), label = "line2")
    val totalText = rememberRolledIn(Money.format(shown), delayMs = 260, enabled = intro)
    val description = buildString {
        append("Spent ").append(Money.format(shown)).append(' ').append(line1)
        if (line2 != null) append(", ").append(line2)
    }

    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp)
            .aspectRatio(1f)
            .semantics(mergeDescendants = true) { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        SpendRing(
            s, Modifier.fillMaxSize(),
            stroke = 22.dp, gap = 3.dp, knobRadius = 9.dp, inset = 8.dp,
            highlight = filter, ticks = true,
            playIntro = intro, onIntroStarted = onIntro,
        )
        Column(
            Modifier.padding(horizontal = 52.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RollingText(totalText, style = heroStyle(totalText), color = c.ink)
            Spacer(Modifier.height(6.dp))
            AnimatedContent(
                targetState = line1,
                transitionSpec = {
                    (fadeIn(tween(220, delayMillis = 40)) + slideInVertically(tween(280, easing = Motion.EaseOut)) { it / 2 }) togetherWith
                        (fadeOut(tween(120)) + slideOutVertically(tween(180)) { -it / 2 }) using SizeTransform(clip = false)
                },
                label = "dialLine1",
            ) { t ->
                Text(t, style = BodyFigures, color = c.inkMuted, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            AnimatedVisibility(
                visible = line2 != null,
                enter = fadeIn(tween(220)) + scaleIn(Motion.bouncy(), initialScale = 0.85f),
                exit = fadeOut(tween(140)) + scaleOut(tween(160), targetScale = 0.9f),
            ) {
                RollingText(line2.orEmpty(), style = BodyStrongFigures, color = line2Color, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

/** Big enough to be the point of the page, small enough to sit inside the ring. */
private fun heroStyle(text: String) = when {
    text.length <= 7 -> HubType.clockLarge
    text.length <= 9 -> HubType.clockLarge.copy(fontSize = 52.sp, lineHeight = 52.sp)
    else -> HubType.clockMedium.copy(fontSize = 42.sp, lineHeight = 42.sp)
}

// ---------------------------------------------------------------------------------------------
// Categories
// ---------------------------------------------------------------------------------------------

/**
 * A category: badge, name, amount and one quiet line, with a budget bar when it has a budget.
 * Tapping filters the transactions; the selected row rises to the raised tone and the rest dim.
 */
@Composable
private fun CategoryRow(
    t: CategoryTotal,
    total: Long,
    selected: Boolean,
    dimmed: Boolean,
    index: Int,
    grow: Boolean,
    onGrown: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Hub.colors
    val bg by animateColorAsState(if (selected) c.raised else c.ground, tween(220), label = "catBg")
    val contentAlpha by animateFloatAsState(if (dimmed) 0.45f else 1f, tween(240), label = "catAlpha")
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
    val captionColor by animateColorAsState(if (over) c.danger else c.inkMuted, tween(240), label = "catCaption")

    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .pressable(
                onClick = onClick,
                pressedScale = 0.98f,
                showRipple = false,
                onClickLabel = if (selected) "Show all transactions" else "Show ${t.category.label} only",
            )
            .semantics { this.selected = selected }
            .clip(RowShape)
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .graphicsLayer { alpha = contentAlpha },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryBadge(t.category, size = 42.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    t.category.label,
                    style = HubType.bodyStrong,
                    color = c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                RollingText(Money.format(t.spentPaise), style = HubType.clockSmall, color = c.ink)
            }
            Text(caption, style = SmallFigures, color = captionColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (budget != null) {
                Spacer(Modifier.height(10.dp))
                BudgetBar(
                    fraction = t.spentPaise.toFloat() / budget,
                    color = if (over) c.danger else t.category.color(c),
                    grow = grow,
                    index = index,
                    onGrown = onGrown,
                )
            }
        }
    }
}

/** A filled rounded bar on a raised2 track that grows in from nothing, then follows on a spring. */
@Composable
private fun BudgetBar(fraction: Float, color: Color, grow: Boolean, index: Int, onGrown: () -> Unit) {
    val c = Hub.colors
    val reduced = LocalReducedMotion.current
    val target = fraction.coerceIn(0f, 1f)
    val progress = remember { Animatable(if (grow && !reduced) 0f else target) }
    val fill by animateColorAsState(color, tween(260), label = "barColor")
    LaunchedEffect(target) {
        onGrown()
        when {
            reduced -> progress.snapTo(target)
            grow && progress.value == 0f -> {
                delay(320L + index * 70L)
                progress.animateTo(target, tween(860, easing = Motion.EaseOut))
            }
            else -> progress.animateTo(target, Motion.gentle())
        }
    }
    Canvas(Modifier.fillMaxWidth().height(8.dp)) {
        val h = size.height
        val corner = CornerRadius(h / 2f)
        drawRoundRect(c.raised2, size = size, cornerRadius = corner)
        val w = size.width * progress.value
        if (w > 0.5f) drawRoundRect(fill, size = Size(w, h), cornerRadius = corner)
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
            .padding(start = Gutter, end = Gutter - 4.dp, top = 28.dp, bottom = 4.dp)
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(
            targetState = filter,
            transitionSpec = {
                if (reduced) {
                    fadeIn(tween(160)) togetherWith fadeOut(tween(100)) using SizeTransform(clip = false)
                } else {
                    (fadeIn(tween(220, delayMillis = 40)) + slideInVertically(tween(300, easing = Motion.EaseOut)) { it / 2 }) togetherWith
                        (fadeOut(tween(120)) + slideOutVertically(tween(200)) { -it / 2 }) using SizeTransform(clip = false)
                }
            },
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier.weight(1f),
            label = "txTitle",
        ) { f ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (f != null) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(f.color(c)))
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    f?.label ?: "Transactions",
                    style = HubType.section,
                    color = c.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        AnimatedVisibility(
            visible = filter != null,
            enter = fadeIn(tween(180)) + scaleIn(if (reduced) tween(0) else Motion.bouncy(), initialScale = 0.8f),
            exit = fadeOut(tween(120)) + scaleOut(tween(140), targetScale = 0.85f),
        ) {
            Row(
                Modifier
                    .pressable(onClick = onClear, pressedScale = 0.93f, showRipple = false, onClickLabel = "Show all transactions")
                    .heightIn(min = 40.dp)
                    .clip(CircleShape)
                    .background(c.raised2)
                    .padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Close, contentDescription = null, tint = c.ink, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Show all", style = HubType.button.copy(fontSize = 14.sp), color = c.ink)
            }
        }
    }
}

private fun dayLabel(d: LocalDate, today: LocalDate): String = when (d) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> d.format(
        DateTimeFormatter.ofPattern(if (d.year == today.year) "EEE d MMM" else "EEE d MMM yyyy", Locale.getDefault()),
    )
}

/** The day, and what it cost, rolling as expenses come and go. */
@Composable
private fun DayHeader(date: LocalDate, today: LocalDate, totalPaise: Long, modifier: Modifier = Modifier) {
    val c = Hub.colors
    Row(
        modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = 18.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(dayLabel(date, today), style = BodyStrongFigures, color = c.inkMuted, modifier = Modifier.weight(1f))
        RollingText(Money.format(totalPaise), style = SmallFigures, color = c.inkMuted)
    }
}

/**
 * One expense: its category badge, the note (or the category), the time, and the amount.
 * Tap to edit. Swipe left to delete: the danger ground shows through and the bin grows and
 * straightens as you drag, with a tick of haptics at the point of no return.
 */
@Composable
private fun TransactionRow(
    e: Expense,
    timeText: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Hub.colors
    val haptics = LocalHapticFeedback.current
    val dismiss = rememberSwipeToDismissBoxState()
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
    val title = e.note.ifBlank { e.category.label }

    SwipeToDismissBox(
        state = dismiss,
        modifier = modifier
            .padding(horizontal = 8.dp)
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
                        .padding(end = 26.dp)
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
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RowShape)
                .background(c.ground)
                .pressable(onClick = onEdit, pressedScale = 0.98f, onClickLabel = "Edit expense")
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryBadge(e.category, size = 44.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = HubType.bodyStrong, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(timeText, style = SmallFigures, color = c.inkMuted, maxLines = 1)
            }
            Spacer(Modifier.width(12.dp))
            Text(Money.format(e.amountPaise), style = HubType.clockSmall, color = c.ink, maxLines = 1)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Budgets
// ---------------------------------------------------------------------------------------------

private fun budgetText(paise: Long) = Money.format(paise).removePrefix("₹")

/**
 * Monthly limits: one for everything and one per category. Empty means no limit. Only the
 * fields you changed are saved.
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
    var total by remember { mutableStateOf(initialTotal) }
    val cats = remember { mutableStateMapOf<ExpenseCategory, String>().apply { putAll(initialCats) } }

    fun bad(text: String) = text.isNotBlank() && Money.parse(text) == null
    val anyBad = bad(total) || cats.values.any { bad(it) }
    val catSum = cats.values.sumOf { Money.parse(it) ?: 0L }
    val totalLimit = Money.parse(total)
    val sumOver = totalLimit != null && catSum > totalLimit
    val sumColor by animateColorAsState(if (sumOver) c.danger else c.inkMuted, tween(220), label = "sumColor")

    HubSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Text("Monthly budgets", style = HubType.section, color = c.ink, modifier = Modifier.staggerIn(stagger, "title", 0))
            Spacer(Modifier.height(6.dp))
            Text(
                "You'll get a notification at 80% and at 100%. Leave a field empty for no limit.",
                style = BodyFigures,
                color = c.inkMuted,
                modifier = Modifier.staggerIn(stagger, "intro", 1),
            )
            Spacer(Modifier.height(20.dp))
            BudgetField(
                value = total,
                onChange = { total = it },
                label = "Everything",
                isError = bad(total),
                leading = { Text("₹", style = HubType.clockSmall, color = c.ink) },
                modifier = Modifier.staggerIn(stagger, "total", 2),
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 12.dp).staggerIn(stagger, "perCat", 3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Per category", style = HubType.bodyStrong, color = c.ink, modifier = Modifier.weight(1f))
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
                    leading = { CategoryBadge(cat, size = 32.dp) },
                    modifier = Modifier.padding(bottom = 10.dp).staggerIn(stagger, cat, 4 + i),
                )
            }
            PrimaryButton(
                text = "Save budgets",
                enabled = !anyBad,
                container = c.ink,
                content = c.ground,
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
