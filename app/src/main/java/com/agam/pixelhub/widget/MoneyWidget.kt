package com.agam.pixelhub.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.agam.pixelhub.container
import com.agam.pixelhub.data.Expense
import com.agam.pixelhub.data.ExpenseCategory
import com.agam.pixelhub.expenses.BudgetMath
import com.agam.pixelhub.expenses.Money
import com.agam.pixelhub.expenses.MonthSummary
import com.agam.pixelhub.data.Budget
import com.agam.pixelhub.ui.Tab
import com.agam.pixelhub.ui.expenses.color
import com.agam.pixelhub.ui.expenses.shortLabel
import com.agam.pixelhub.ui.theme.NightColors
import com.agam.pixelhub.ui.theme.ThemeStore
import com.agam.pixelhub.widget.kit.Caption
import com.agam.pixelhub.widget.kit.CardTone
import com.agam.pixelhub.widget.kit.Figure
import com.agam.pixelhub.widget.kit.Notice
import com.agam.pixelhub.widget.kit.WidgetCard
import com.agam.pixelhub.widget.kit.WidgetPalette
import com.agam.pixelhub.widget.kit.WidgetShape
import com.agam.pixelhub.widget.kit.openTab
import com.agam.pixelhub.widget.kit.shapeOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import java.time.YearMonth
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import androidx.glance.color.ColorProvider as DayNightColor

/** Android's entry point for the Money widget. */
class MoneyWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MoneyWidget()
}

class MoneyWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val expenses = context.container.expenses
        val first = expenses.month().first()
        // Re-reads the month every minute, so a session still open at midnight on the 1st moves on.
        val month = flow {
            while (true) {
                emit(YearMonth.now())
                delay(60_000L)
            }
        }.distinctUntilChanged().flatMapLatest { expenses.month(it) }
        provideContent {
            // Follows this month live: a new expense or budget redraws at once.
            val summary by remember { month }.collectAsState(first)
            MoneyContent(context, summary)
        }
    }

    /** The widget picker shows the real widget with sample figures. */
    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent { MoneyContent(context, sampleSummary()) }
    }
}

@Composable
private fun MoneyContent(context: Context, s: MonthSummary) {
    val palette = WidgetPalette.of(context, CardTone.CHROME)
    val size = LocalSize.current
    val shape = shapeOf(size)
    val open = openTab(context, Tab.EXPENSES)
    if (s.totalPaise == 0L && s.budgetPaise == null) {
        Notice(palette, "Nothing spent yet", "Tap to log an expense", open, shape)
        return
    }
    val f = MoneyFigures(context, s)
    WidgetCard(palette, open, f.description, padding = if (shape == WidgetShape.TINY) 10.dp else 14.dp) {
        when (shape) {
            WidgetShape.TINY -> Column(GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
                Figure(compactRupees(s.totalPaise), palette, 20.sp)
                Caption("spent", palette)
            }
            WidgetShape.STRIP_S -> Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Figure(Money.format(s.totalPaise), palette, 20.sp)
                LeftLine(palette, f)
            }
            WidgetShape.STRIP_M, WidgetShape.STRIP_L -> Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Figure(Money.format(s.totalPaise), palette, 20.sp)
                    Spacer(GlanceModifier.defaultWeight())
                    s.budgetPaise?.let { Caption("of ${Money.format(it)}", palette) }
                }
                Spacer(GlanceModifier.height(6.dp))
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (s.budgetPaise != null) {
                        BudgetBar(palette, f, GlanceModifier.defaultWeight())
                        Spacer(GlanceModifier.width(10.dp))
                    }
                    LeftLine(palette, f)
                    if (shape == WidgetShape.STRIP_L && s.incomePaise > 0) {
                        Spacer(GlanceModifier.width(12.dp))
                        Caption("${Money.format(s.incomePaise)} in", palette, strong = true)
                    }
                }
            }
            WidgetShape.SQUARE -> MonthBlock(palette, f, s, GlanceModifier.fillMaxSize())
            WidgetShape.PANEL, WidgetShape.LARGE -> Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                MonthBlock(palette, f, s, GlanceModifier.defaultWeight())
                Spacer(GlanceModifier.width(16.dp))
                Column(GlanceModifier.defaultWeight()) {
                    val rows = if (shape == WidgetShape.LARGE) 4 else 3
                    s.byCategory.filter { it.spentPaise > 0 }.take(rows).forEach { ct ->
                        Row(GlanceModifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(GlanceModifier.size(8.dp).cornerRadius(4.dp).background(dotColor(ct.category))) {}
                            Spacer(GlanceModifier.width(8.dp))
                            Caption(ct.category.shortLabel, palette, strong = true)
                            Spacer(GlanceModifier.defaultWeight())
                            Caption(Money.format(ct.spentPaise), palette)
                        }
                    }
                    if (shape == WidgetShape.LARGE) {
                        if (s.incomePaise > 0) {
                            Spacer(GlanceModifier.height(6.dp))
                            Caption("${Money.format(s.incomePaise)} came in", palette)
                        }
                        Spacer(GlanceModifier.height(10.dp))
                        AddButton(palette, open)
                    }
                }
            }
        }
    }
}

/** Everything the layouts print, worked out once. */
private class MoneyFigures(context: Context, s: MonthSummary) {
    val month: String = s.month.month.getDisplayName(JavaTextStyle.FULL, Locale.getDefault())
    val budget = s.budgetPaise
    val over = budget != null && s.totalPaise > budget
    /** "₹7,550 left", "₹1,200 over", or "this month" without a budget. */
    val left: String = when {
        budget == null -> "this month"
        over -> "${Money.format(s.totalPaise - budget)} over"
        else -> "${Money.format(budget - s.totalPaise)} left"
    }
    val progress: Float = budget?.takeIf { it > 0 }?.let { (s.totalPaise.toFloat() / it).coerceIn(0f, 1f) } ?: 0f
    /** Red for "over": the theme's dark-background red, since this card is dark in every theme. */
    val danger: ColorProvider = ThemeStore.theme(context).value.night
        .let { if (it.isDark) it.danger else NightColors.danger }
        .let { DayNightColor(it, it) }
    val description: String = buildString {
        append("${Money.format(s.totalPaise)} spent in $month")
        if (budget != null) append(", $left of ${Money.format(budget)} budget")
    }
}

/** The budget line, in red when over. */
@Composable
private fun LeftLine(palette: WidgetPalette, f: MoneyFigures) {
    if (f.over) {
        Text(f.left, maxLines = 1, style = TextStyle(color = f.danger, fontSize = 12.sp, fontWeight = FontWeight.Bold))
    } else {
        Caption(f.left, palette, strong = f.budget != null)
    }
}

@Composable
private fun BudgetBar(palette: WidgetPalette, f: MoneyFigures, modifier: GlanceModifier = GlanceModifier) {
    LinearProgressIndicator(
        progress = f.progress,
        modifier = modifier.height(6.dp),
        color = if (f.over) f.danger else palette.ink,
        backgroundColor = palette.track,
    )
}

/** Month, total, bar and what's left: the 2×2, and the left half of the wider sizes. */
@Composable
private fun MonthBlock(palette: WidgetPalette, f: MoneyFigures, s: MonthSummary, modifier: GlanceModifier) {
    Column(modifier, verticalAlignment = Alignment.CenterVertically) {
        Caption(f.month, palette)
        Figure(Money.format(s.totalPaise), palette, 24.sp)
        if (f.budget != null) {
            Spacer(GlanceModifier.height(8.dp))
            BudgetBar(palette, f, GlanceModifier.fillMaxWidth())
            Spacer(GlanceModifier.height(6.dp))
        }
        LeftLine(palette, f)
    }
}

/** "+ Add": opens Money, where Add is the first action. */
@Composable
private fun AddButton(palette: WidgetPalette, open: androidx.glance.action.Action) {
    Box(
        GlanceModifier
            .height(36.dp)
            .cornerRadius(18.dp)
            .background(palette.track)
            .clickable(open)
            .semantics { contentDescription = "Add an expense" }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Caption("+ Add", palette, strong = true)
    }
}

/** A category's dot: its dark-background colour, since this card is dark in every theme. */
private fun dotColor(category: ExpenseCategory): ColorProvider =
    category.color(NightColors).let { DayNightColor(it, it) }

/** "₹950", "₹12.4k", "₹1.2L": the total in the smallest tile. */
private fun compactRupees(paise: Long): String {
    val rupees = paise / 100.0
    return when {
        rupees < 1_000 -> Money.format(paise)
        // Locale.ROOT: a German or Marathi phone would print "12,4k" or "१२.४k". Below 99,950 so
        // that ₹99,960 reads "₹1L", not "₹100k".
        rupees < 99_950 -> "₹" + String.format(Locale.ROOT, "%.1fk", rupees / 1_000).replace(".0k", "k")
        else -> "₹" + String.format(Locale.ROOT, "%.1fL", rupees / 1_00_000).replace(".0L", "L")
    }
}

private fun sampleSummary(): MonthSummary {
    val now = System.currentTimeMillis()
    fun spend(rupees: Long, cat: ExpenseCategory) = Expense(amountPaise = rupees * 100, category = cat, spentAt = now)
    return BudgetMath.summarize(
        YearMonth.now(),
        listOf(spend(5_200, ExpenseCategory.FOOD), spend(3_100, ExpenseCategory.BILLS), spend(2_000, ExpenseCategory.TRAVEL), spend(2_150, ExpenseCategory.SHOPPING)),
        listOf(Budget(Budget.TOTAL, 20_000 * 100)),
    )
}
