package com.agam.pixelhub.ui.expenses

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agam.pixelhub.expenses.Money
import com.agam.pixelhub.expenses.MonthReport
import com.agam.pixelhub.expenses.changeText
import com.agam.pixelhub.ui.HubSheet
import com.agam.pixelhub.ui.theme.Hub
import com.agam.pixelhub.ui.theme.HubType
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())

/** The month in review: totals beside last month, where it went, and the standouts. */
@Composable
internal fun ReportSheet(report: MonthReport, thisMonth: YearMonth, onDismiss: () -> Unit) {
    val c = Hub.colors
    val r = report
    val s = r.summary
    val zone = ZoneId.systemDefault()
    HubSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Text(
                r.month.format(DateTimeFormatter.ofPattern(if (r.month.year == thisMonth.year) "MMMM" else "MMMM yyyy", Locale.getDefault())) + " report",
                style = SheetTitle,
                color = c.ink,
            )
            Text(
                if (r.partial) "So far, over the first ${r.days} ${if (r.days == 1) "day" else "days"}" else "The month in review",
                style = HubType.small,
                color = c.inkMuted,
            )

            // Spent, and how it compares.
            Spacer(Modifier.height(20.dp))
            Text("Spent", style = HubType.small, color = c.inkMuted)
            Text(Money.format(s.totalPaise), style = HubType.clockMedium.copy(fontFeatureSettings = "tnum"), color = c.ink, maxLines = 1)
            r.changeText()?.let { text ->
                val down = (r.changePercent ?: 0) <= 0
                Text(
                    text,
                    style = HubType.small.copy(fontWeight = FontWeight.Medium),
                    color = if (down) c.ok else c.danger,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .clip(CircleShape)
                        .background(if (down) c.incomeSoft() else c.dangerSoft)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }

            Spacer(Modifier.height(16.dp))
            // Money in and the net only once anything came in; until then net is just spent, negated.
            if (s.incomePaise > 0) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat("Received", incomeText(s.incomePaise), c.ok, Modifier.weight(1f))
                    Stat("Net", signedText(s.netPaise), if (s.netPaise < 0) c.danger else c.ink, Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("Daily average", Money.format(r.dailyAveragePaise), c.ink, Modifier.weight(1f))
                Stat("Entries", s.expenses.size.toString(), c.ink, Modifier.weight(1f))
            }

            // Budget.
            val total = s.budgetPaise
            val left = r.budgetLeftPaise
            if (total != null && left != null) {
                Spacer(Modifier.height(8.dp))
                val over = left < 0
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (over) c.dangerSoft else c.raised2)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(
                        if (over) "Over budget by ${Money.format(-left)}" else "Under budget by ${Money.format(left)}",
                        style = HubType.bodyStrong,
                        color = if (over) c.danger else c.ink,
                    )
                    Text("Budget ${Money.format(total)}", style = RowMeta, color = c.inkMuted)
                }
            }

            if (s.expenses.isEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text("Nothing spent this month.", style = HubType.body, color = c.inkMuted)
                return@Column
            }

            // Where it went.
            Spacer(Modifier.height(24.dp))
            Text("Where it went", style = HubType.section, color = c.ink)
            Spacer(Modifier.height(6.dp))
            r.top.forEach { t ->
                ReportRow(
                    well = { CategoryWell(t.category) },
                    title = t.category.label,
                    meta = if (t.previousPaise > 0) "Was ${Money.format(t.previousPaise)}" else "Nothing last month",
                    amount = Money.format(t.paise),
                )
            }

            // Standouts.
            Spacer(Modifier.height(20.dp))
            Text("Standouts", style = HubType.section, color = c.ink)
            Spacer(Modifier.height(6.dp))
            r.biggest?.let { e ->
                val day = Instant.ofEpochMilli(e.spentAt).atZone(zone).toLocalDate()
                ReportRow(
                    well = { CategoryWell(e.category) },
                    title = "Biggest: " + e.note.ifBlank { e.category.label },
                    meta = day.format(DayFormat),
                    amount = Money.format(e.amountPaise),
                )
            }
            r.busiestDay?.let { d ->
                ReportRow(
                    well = { DateWell(d.date) },
                    title = "Busiest day",
                    meta = d.date.format(DayFormat),
                    amount = Money.format(d.paise),
                )
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    val c = Hub.colors
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(c.raised2)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(label, style = RowMeta, color = c.inkMuted, maxLines = 1)
        Text(
            value,
            style = BodyStrongFigures,
            color = color,
            maxLines = 1,
            softWrap = false,
            // Ellipsis, not a silent cut: a clipped amount reads as a different amount.
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ReportRow(well: @Composable () -> Unit, title: String, meta: String, amount: String) {
    val c = Hub.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        well()
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = RowTitle, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(meta, style = RowMeta, color = c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Text(amount, style = BodyStrongFigures, color = c.ink, maxLines = 1)
    }
}

/** The day of the month in a round well, for the busiest day. */
@Composable
private fun DateWell(date: LocalDate) {
    val c = Hub.colors
    Box(Modifier.size(42.dp).clip(CircleShape).background(c.well), contentAlignment = Alignment.Center) {
        Text(date.dayOfMonth.toString(), style = HubType.bodyStrong.copy(fontSize = 15.sp, fontFeatureSettings = "tnum"), color = c.ink)
    }
}
