// SPDX-License-Identifier: GPL-3.0-or-later
package dev.alalauncher.ui.applist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.rememberCoroutineScope
import dev.alalauncher.service.HapticUtil
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.alalauncher.data.EdgeSide
import kotlin.math.exp
import kotlin.math.roundToInt

/** Letters sit this far in from the screen edge so they aren't crowding it. */
private const val EDGE_INSET_DP = 28f

/**
 * How far the bulge pushes the column away from the edge at its peak.
 *
 * Has to clear the fingertip without reaching the bubble. The thumb is already an edge-zone in
 * from the screen edge, so at 75dp the peak landed about 7dp from it — underneath, hidden by
 * the hand pointing at it — while the bubble beginning at 122dp left only about 19dp of room
 * to move into. The two only go further out together, which is what [SCRUB_BUBBLE_INSET_DP]
 * is for.
 */
private const val BELL_AMPLITUDE_DP = 112f

/** Half the letter cell, so the peak is measured by its edge rather than its middle. */
private const val LETTER_HALF_DP = 10f

/**
 * Where the letter bubble starts, kept a fixed gap beyond the peak of the bell.
 *
 * Derived rather than written down twice: the bubble sat at a number of its own, so growing
 * the bulge to clear a fingertip walked the strip straight into it.
 */
private const val LETTER_TOUCH_WIDTH_DP = 26f

const val SCRUB_BUBBLE_INSET_DP = EDGE_INSET_DP + BELL_AMPLITUDE_DP + LETTER_HALF_DP + 18f

/**
 * The A-Z strip. The letters never change size — the *column* bows outward around the
 * fingertip on a gaussian, so the alphabet traces a bell curve and settles flat again when
 * the finger lifts. Scaling the glyphs (what this used to do) just stretched them until
 * they looked pixelated.
 *
 * [scrubY] and [pullPx] are read as lambdas inside a graphicsLayer so the whole strip
 * animates in the draw phase; recomposing 26 Text nodes per pointer move made this jitter.
 */
@Composable
fun EdgeScrubber(
    letters: List<Char>,
    scrubY: () -> Float?,
    pullPx: () -> Float,
    band: ScrubBand,
    side: EdgeSide,
    /**
     * How far in from the screen edge the letters sit.
     *
     * Zero on the home screen, so the strip is against the edge where the eye expects an edge
     * to be. The drawer keeps the inset, because there the letters sit over a list that is
     * being scrolled and the margin keeps them off whatever row happens to be underneath.
     */
    edgeInsetDp: Float = EDGE_INSET_DP,
    /**
     * The gesture holder. Null leaves the strip decorative, which is what the settings screen
     * and any preview want — only the live strip should answer a finger.
     */
    state: ScrubState? = null,
    /** Opens the list if a letter was tapped while it was closed. */
    onOpen: (() -> Unit)? = null,
    /** Whether the list was already up when the finger landed. Read through [rememberUpdatedState]. */
    listOpen: Boolean = true,
    hapticsEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    if (letters.isEmpty()) return
    val density = LocalDensity.current.density
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val spacingPx = band.heightPx / letters.size
    // Wide enough that a good stretch of the alphabet takes part in the curve.
    val sigmaPx = 2.6f * spacingPx
    val bellPx = BELL_AMPLITUDE_DP * density
    val currentOpen by rememberUpdatedState(listOpen)
    val currentOnOpen by rememberUpdatedState(onOpen)

    // The composable can leave composition with the finger still down: opening the drawer
    // removes the home strip while a swipe is in flight, and Compose cancels the gesture's
    // coroutine rather than letting it finish. Without this, scrub stays active, the strip
    // keeps bowing at the last letter, and nothing answers a drag until the finger lifts and
    // a fresh gesture starts. Drawing first and catching only the taps that land on nothing
    // matters here too: this holder must be cleaned up by the composable that owns it.
    state?.let { holder ->
        DisposableEffect(holder) {
            onDispose { holder.stop() }
        }
    }

    Box(
        modifier = modifier
            // Wide enough for the inset, the letter cell and the whole outward bulge — at
            // 56dp the horizontal padding ate the entire width and the curve had nowhere to
            // go, and the bulge has since grown enough to clear a fingertip.
            .width(210.dp)
            .fillMaxHeight()
            .padding(horizontal = edgeInsetDp.dp),
    ) {
        // The letters themselves are the control. They used to be drawn and nothing more, so
        // the only way to reach a letter was to start a drag at the screen edge and carry it
        // across — a gesture for opening a list, not for choosing a letter in one.
        //
        // On its own Box rather than on the container, and only as tall as the band. The
        // container is 210dp by the whole screen: putting the handler on it would make the
        // strip swallow every tap down the right-hand side of the home screen, which is a
        // great deal worse than a strip nobody could point at.
        if (state != null) {
            val holder = state
            Box(
                modifier = Modifier
                    .align(if (side == EdgeSide.LEFT) Alignment.TopStart else Alignment.TopEnd)
                    .offset { IntOffset(0, band.topPx.roundToInt()) }
                    .width(LETTER_TOUCH_WIDTH_DP.dp)
                    .height((band.heightPx / density).dp)
                    .pointerInput(letters, band, side, holder) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            // Read before opening: a tap that opened the list must not then be
                            // read as a tap on one that was already open.
                            val openAtDown = currentOpen
                            holder.begin(side)
                            if (!openAtDown) currentOnOpen?.invoke()

                            var lastIndex = -1
                            var dragging = false
                            // The box starts at the top of the band, so a local y has to be put
                            // back into the space the geometry is written in before it means
                            // anything. Getting this wrong reads every letter as A.
                            fun report(localY: Float) {
                                val y = localY + band.topPx
                                val index = ScrubberGeometry.indexForY(
                                    y, band.topPx, band.heightPx, letters.size,
                                )
                                if (index == lastIndex || index !in letters.indices) return
                                lastIndex = index
                                holder.update(y, 0f, letters[index])
                                HapticUtil.tick(view, hapticsEnabled)
                            }

                            report(down.position.y)

                            val pointer = down
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == pointer.id } ?: break
                                if (!change.pressed) break
                                if (change.positionChanged()) {
                                    // Only a finger that has actually travelled counts as a
                                    // scrub, so a plain tap does not spend a frame fading the
                                    // rest of the list away and straight back in.
                                    if (!dragging) {
                                        dragging = true
                                        holder.markScrubbing()
                                    }
                                    report(change.position.y)
                                }
                                change.consume()
                            }
                            // Not release(): that suspends on a spring in a scope this composable
                            // owns, and opening the drawer takes the strip out of composition —
                            // cancelling the very coroutine that was going to clear it.
                            holder.stop()
                        }
                    },
            )
        }
        letters.forEachIndexed { index, c ->
            val centerY = ScrubberGeometry.letterCenterY(index, band.topPx, band.heightPx, letters.size)

            Box(
                modifier = Modifier
                    .align(if (side == EdgeSide.LEFT) Alignment.TopStart else Alignment.TopEnd)
                    .offset { IntOffset(0, (centerY - spacingPx / 2f).roundToInt()) }
                    .width(20.dp)
                    .graphicsLayer {
                        val y = scrubY()
                        val gain = if (y == null) {
                            0f
                        } else {
                            val d = y - centerY
                            exp(-(d * d) / (2f * sigmaPx * sigmaPx))
                        }

                        // Position only — the glyph is never scaled.
                        val outward = bellPx * gain + pullPx() * gain
                        translationX = if (side == EdgeSide.LEFT) outward else -outward
                        alpha = 0.55f + 0.45f * gain
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(text = c.toString(), fontSize = 13.sp, color = Color.White)
            }
        }
    }
}