// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.victorialauncher.media.NotificationEntry

/**
 * The compact notification preview shown under an app label when that app has an unread
 * notification: the posting context and how long ago it arrived, the notification's text,
 * and a chevron promising more on a tap.
 *
 * Deliberately a single composable so the home screen and the drawer stay in step: both rows
 * draw the same shape from the same inputs, rather than two copies that drift.
 */
@Composable
fun NotificationPreviewLine(
    entry: NotificationEntry,
    contentColor: Color,
    nowMs: Long = System.currentTimeMillis(),
    fontSize: TextUnit = 11.sp,
    modifier: Modifier = Modifier,
) {
    val heading = entry.title?.toString()?.trim().orEmpty()
    val body = entry.text?.toString()?.trim().orEmpty()
    val head = if (heading.isNotEmpty()) heading else body
    val tail = if (heading.isNotEmpty() && body.isNotEmpty() && body != heading) body else null

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.Start),
    ) {
        Text(
            text = "$head \u00B7 ${RelativeTime.ago(entry.postTime, nowMs)}",
            color = contentColor.copy(alpha = 0.75f),
            fontSize = fontSize,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (tail != null) {
            Text(
                text = tail,
                color = contentColor.copy(alpha = 0.55f),
                fontSize = fontSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        Spacer(Modifier.width(2.dp))
        Text(
            text = "\u203A", // ›
            color = contentColor.copy(alpha = 0.45f),
            fontSize = fontSize,
        )
    }
}