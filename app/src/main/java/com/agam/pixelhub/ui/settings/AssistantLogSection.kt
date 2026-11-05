package com.agam.pixelhub.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agam.pixelhub.ai.AssistantLogEntry
import com.agam.pixelhub.container
import com.agam.pixelhub.ui.QuietButton
import com.agam.pixelhub.ui.theme.Hub
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** How many entries show before "Show all". */
private const val PREVIEW = 8

private val TIME = DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.ENGLISH)

/**
 * The assistant's request log: every message, whether a rule understood it, what it became,
 * and whether the draft was then saved, cancelled or failed. Kept on the phone, newest first.
 */
@Composable
fun AssistantLogSection(modifier: Modifier = Modifier) {
    val c = LocalContext.current.container
    val entries by c.assistantLog.entries.collectAsStateWithLifecycle()
    var open by rememberSaveable { mutableStateOf(false) }
    var showAll by rememberSaveable { mutableStateOf(false) }
    val understood = entries.count { it.ok }

    SettingsCard(modifier) {
        SettingRow(
            icon = Icons.Outlined.History,
            title = "Assistant log",
            meta = if (entries.isEmpty()) {
                "Nothing yet · works offline with keyword rules"
            } else {
                "$understood understood · ${entries.size - understood} not understood"
            },
            onClick = if (entries.isNotEmpty()) ({ open = !open }) else null,
            onClickLabel = if (open) "Hide log" else "Show log",
            trailing = if (entries.isNotEmpty()) ({ ExpandChevron(open) }) else null,
        )
        Expandable(open && entries.isNotEmpty()) {
            Column(Modifier.fillMaxWidth()) {
                (if (showAll) entries else entries.take(PREVIEW)).forEach { LogRow(it) }
                Row(
                    Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (entries.size > PREVIEW) {
                        QuietButton(if (showAll) "Show fewer" else "Show all ${entries.size}", onClick = { showAll = !showAll })
                    }
                    QuietButton(
                        "Clear log",
                        onClick = {
                            c.assistantLog.clear()
                            open = false
                            showAll = false
                        },
                        color = Hub.colors.danger,
                    )
                }
            }
        }
    }
}

/**
 * One request: what was typed, then what it became (or why it failed), then the rule,
 * how long the rules took, when, and what happened to the draft.
 */
@Composable
private fun LogRow(e: AssistantLogEntry) {
    val colors = Hub.colors
    val time = Instant.ofEpochMilli(e.at).atZone(ZoneId.systemDefault()).format(TIME)
    val took = if (e.micros < 1_000) "${e.micros} µs" else "%.1f ms".format(e.micros / 1_000.0)
    val details = listOfNotNull(
        if (e.ok) "✓ ${e.rule}" else "✗ ${e.rule}",
        took,
        time,
        e.outcome,
    ).joinToString(" · ")
    SettingRow(
        icon = when {
            !e.ok -> Icons.Rounded.Close
            e.outcomeOk == false -> Icons.Rounded.Close
            e.outcome == null || e.outcomeOk == true -> Icons.Rounded.Check
            else -> Icons.Rounded.Remove
        },
        iconTint = when {
            !e.ok || e.outcomeOk == false -> colors.danger
            e.outcomeOk == null && e.outcome != null -> colors.inkMuted
            else -> colors.ok
        },
        title = "“${e.input}”",
        meta = "${e.summary}\n$details",
        role = null,
    )
}
