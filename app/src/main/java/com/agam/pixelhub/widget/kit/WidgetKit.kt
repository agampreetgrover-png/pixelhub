package com.agam.pixelhub.widget.kit

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.agam.pixelhub.MainActivity
import com.agam.pixelhub.ui.Tab
import com.agam.pixelhub.ui.alarms.dawnCard
import com.agam.pixelhub.ui.alarms.onDawnSoft
import com.agam.pixelhub.ui.theme.HubColors
import com.agam.pixelhub.ui.theme.ThemeStore
import kotlin.math.roundToInt
import androidx.glance.color.ColorProvider as DayNightColor

/** The card a widget sits on. Each section keeps its colour from the app. */
enum class CardTone { SUN, DAWN, CHROME, RAISED }

private fun HubColors.fill(tone: CardTone): Color = when (tone) {
    CardTone.SUN -> sun
    CardTone.DAWN -> dawnCard
    CardTone.CHROME -> chrome
    CardTone.RAISED -> raised
}

private fun HubColors.on(tone: CardTone): Color = when (tone) {
    CardTone.SUN -> onSun
    CardTone.DAWN -> onDawn
    CardTone.CHROME -> onChrome
    CardTone.RAISED -> ink
}

private fun HubColors.soft(tone: CardTone): Color = when (tone) {
    CardTone.SUN -> onSun.copy(alpha = 0.72f)
    CardTone.DAWN -> onDawnSoft
    CardTone.CHROME -> onChrome.copy(alpha = 0.68f)
    CardTone.RAISED -> inkMuted
}

private fun HubColors.trackOf(tone: CardTone): Color = when (tone) {
    CardTone.SUN -> onSun.copy(alpha = 0.14f)
    CardTone.DAWN -> onDawn.copy(alpha = 0.22f)
    CardTone.CHROME -> onChrome.copy(alpha = 0.16f)
    CardTone.RAISED -> raised2
}

/**
 * One widget's colours from PixelHub's chosen theme. With the System theme every role is a
 * day/night pair, so the launcher switches them with the phone. [now] gives plain colours
 * for drawing bitmaps such as the ring.
 */
class WidgetPalette(
    private val day: HubColors,
    private val night: HubColors,
    private val tone: CardTone,
    private val isNight: Boolean,
) {
    val card: ColorProvider = pair { it.fill(tone) }
    val ink: ColorProvider = pair { it.on(tone) }
    val muted: ColorProvider = pair { it.soft(tone) }
    val track: ColorProvider = pair { it.trackOf(tone) }

    val now: HubColors get() = if (isNight) night else day
    fun nowInk(): Color = now.on(tone)
    fun nowTrack(): Color = now.trackOf(tone)

    private fun pair(role: (HubColors) -> Color): ColorProvider = DayNightColor(role(day), role(night))

    companion object {
        fun of(context: Context, tone: CardTone): WidgetPalette {
            val theme = ThemeStore.theme(context).value
            val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
            return WidgetPalette(theme.day, theme.night, tone, night)
        }
    }
}

/**
 * How much room the launcher gave, in content terms rather than grid cells: launchers size
 * cells differently, so layouts are chosen from real dp.
 */
enum class WidgetShape { TINY, STRIP_S, STRIP_M, STRIP_L, SQUARE, PANEL, LARGE }

/** One row of content: the widget is a strip. */
fun WidgetShape.isStrip(): Boolean =
    this == WidgetShape.STRIP_S || this == WidgetShape.STRIP_M || this == WidgetShape.STRIP_L

/** Sizes where a widget shows a list; lists start at the top so their reading position is stable. */
val LIST_SHAPES = setOf(WidgetShape.SQUARE, WidgetShape.PANEL, WidgetShape.LARGE)

fun shapeOf(size: DpSize): WidgetShape {
    val w = size.width.value
    val h = size.height.value
    return when {
        h < 100f -> when {
            w < 120f -> WidgetShape.TINY
            w < 200f -> WidgetShape.STRIP_S
            w < 290f -> WidgetShape.STRIP_M
            else -> WidgetShape.STRIP_L
        }
        h < 200f -> when {
            w < 120f -> WidgetShape.TINY
            w < 200f -> WidgetShape.SQUARE
            else -> WidgetShape.PANEL
        }
        else -> if (w < 200f) WidgetShape.SQUARE else WidgetShape.LARGE
    }
}

/** The card every widget is built on: the theme's fill, the launcher's corner radius, one tap target. */
@Composable
fun WidgetCard(
    palette: WidgetPalette,
    onClick: Action,
    description: String,
    padding: Dp = 14.dp,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    /** A drawable instead of the flat theme fill (the Calendar widget's glass). */
    background: ImageProvider? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val base = GlanceModifier
        .fillMaxSize()
        .appWidgetBackground()
        .cornerRadius(android.R.dimen.system_app_widget_background_radius)
    Column(
        (if (background != null) base.background(background) else base.background(palette.card))
            .clickable(onClick)
            .semantics { contentDescription = description }
            .padding(padding),
        verticalAlignment = verticalAlignment,
        content = content,
    )
}

/** The big number. Bold, one line. */
@Composable
fun Figure(text: String, palette: WidgetPalette, size: TextUnit) {
    Text(text, maxLines = 1, style = TextStyle(color = palette.ink, fontSize = size, fontWeight = FontWeight.Bold))
}

/** Everything else: the quiet second size. */
@Composable
fun Caption(text: String, palette: WidgetPalette, strong: Boolean = false, maxLines: Int = 1) {
    Text(
        text,
        maxLines = maxLines,
        style = TextStyle(
            color = if (strong) palette.ink else palette.muted,
            fontSize = 12.sp,
            fontWeight = if (strong) FontWeight.Bold else FontWeight.Medium,
        ),
    )
}

/** Empty and no-access states: what's missing, and the tap fixes it. Never a blank tile. */
@Composable
fun Notice(
    palette: WidgetPalette,
    title: String,
    body: String,
    onClick: Action,
    shape: WidgetShape,
    background: ImageProvider? = null,
) {
    WidgetCard(palette, onClick, "$title. $body", background = background) {
        Caption(title, palette, strong = true, maxLines = 2)
        if (shape != WidgetShape.TINY) {
            Spacer(GlanceModifier.height(2.dp))
            Caption(body, palette, maxLines = 2)
        }
    }
}

/** Opens PixelHub on [tab]. A distinct action per tab so launchers don't merge the intents. */
fun openTab(context: Context, tab: Tab): Action = actionStartActivity(
    Intent(context, MainActivity::class.java)
        .setAction("com.agam.pixelhub.widget.OPEN_${tab.name}")
        .putExtra(MainActivity.EXTRA_TAB, tab.name)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
)

/** A progress ring drawn at its exact pixel size (Glance can't draw arcs), so it stays sharp. */
fun ringBitmap(context: Context, size: Dp, fraction: Float, track: Color, progress: Color, stroke: Dp): Bitmap {
    val density = context.resources.displayMetrics.density
    val px = (size.value * density).roundToInt().coerceAtLeast(1)
    val sw = stroke.value * density
    val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val rect = RectF(sw / 2f, sw / 2f, px - sw / 2f, px - sw / 2f)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = sw
        strokeCap = Paint.Cap.ROUND
    }
    paint.color = track.toArgb()
    canvas.drawArc(rect, 0f, 360f, false, paint)
    val f = fraction.coerceIn(0f, 1f)
    if (f > 0f) {
        paint.color = progress.toArgb()
        canvas.drawArc(rect, -90f, 360f * f, false, paint)
    }
    return bitmap
}
