// SPDX-License-Identifier: GPL-3.0-or-later
package dev.alalauncher.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.alalauncher.R
import dev.alalauncher.ui.theme.GlowAccent
import kotlin.math.PI
import kotlin.math.sin

/**
 * What a new install sees exactly once.
 *
 * Two pages and no more. The first is the launcher saying hello and showing what it is - the wave
 * drawn in dots, arriving left to right, which is the same mark as the icon and the same motion
 * as the sea it stands for. The second is the list of what it does, on one scrollable page, and
 * then you are on the home screen and it never appears again.
 *
 * It is drawn over the home screen rather than pushed onto the navigation stack, because the HOME
 * key unwinds that stack - a destination here would be dismissed by the very key that brings the
 * launcher up.
 */
@Composable
fun WelcomeFlow(onDismiss: () -> Unit) {
    var page by remember { mutableIntStateOf(0) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (page == 0) {
            WelcomeHero(onNext = { page = 1 })
        } else {
            WelcomeFeatures(onDone = onDismiss)
        }
    }
}

/**
 * The hello: the wave arriving, a line about what this is, and Next in the corner.
 *
 * The corner rather than under the text because it is the only thing to do on this page, and a
 * button in the bottom corner is reachable with a thumb without looking at where it is.
 */
@Composable
private fun WelcomeHero(onNext: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            WaveMark(Modifier.size(width = 236.dp, height = 104.dp))

            Spacer(Modifier.height(40.dp))

            Text(
                stringResource(R.string.welcome_headline),
                color = Color.White,
                fontSize = 30.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(12.dp))

            Text(
                stringResource(R.string.welcome_sub),
                color = Color.White.copy(alpha = 0.62f),
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
            )
        }

        Text(
            stringResource(R.string.welcome_next),
            color = GlowAccent,
            fontSize = 16.sp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 32.dp, bottom = 36.dp)
                .background(GlowAccent.copy(alpha = 0.10f), MaterialThemeShape())
                .clickableText(onNext)
                .padding(horizontal = 22.dp, vertical = 12.dp),
        )
    }
}

/**
 * The list, on one scrollable page, and Done.
 *
 * Scrollable because the number of things worth saying is not fixed and a second page of prose is
 * worse than a list you can skim.
 */
@Composable
private fun WelcomeFeatures(onDone: () -> Unit) {
    val lines = listOf(
        R.string.welcome_f_favourites,
        R.string.welcome_f_page,
        R.string.welcome_f_apps,
        R.string.welcome_f_notifications,
        R.string.welcome_f_panel,
        R.string.welcome_f_private,
        R.string.welcome_f_offline,
    )

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp)
            .navigationBarsPadding(),
    ) {
        Spacer(Modifier.height(72.dp))

        Text(
            stringResource(R.string.welcome_features_title),
            color = Color.White,
            fontSize = 26.sp,
        )

        Spacer(Modifier.height(6.dp))

        Text(
            stringResource(R.string.welcome_features_sub),
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 14.sp,
        )

        Spacer(Modifier.height(28.dp))

        lines.forEach { id ->
            Row(Modifier.padding(vertical = 9.dp)) {
                Box(
                    Modifier
                        .padding(top = 8.dp, end = 14.dp)
                        .size(6.dp)
                        .background(GlowAccent, MaterialThemeShape()),
                )
                Text(
                    stringResource(id),
                    color = Color.White.copy(alpha = 0.86f),
                    fontSize = 15.sp,
                )
            }
        }

        Spacer(Modifier.height(40.dp))

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Text(
                stringResource(R.string.welcome_done),
                color = GlowAccent,
                fontSize = 16.sp,
                modifier = Modifier
                    .background(GlowAccent.copy(alpha = 0.10f), MaterialThemeShape())
                    .clickableText(onDone)
                    .padding(horizontal = 26.dp, vertical = 12.dp),
            )
        }

        Spacer(Modifier.height(48.dp))
    }
}

/**
 * The mark: a wave drawn in dots, arriving along itself.
 *
 * One progress value drives the lot, and each dot is lit by how far past it that value has come -
 * so the wave forms rather than the whole thing fading up at once, which is the difference between
 * a logo appearing and a logo arriving. The lit order is **bottom to top**, so the trough fills
 * first and the crest rises out of it.
 *
 * Dots are also the icon, so the splash and the launcher icon are the same shape rather than two
 * drawings of it.
 *
 * The dots grow as they arrive and carry a soft ring, because on a black screen a dot of flat
 * white is a dot and a lit one is light.
 */
@Composable
private fun WaveMark(modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(durationMillis = 1700, easing = LinearOutSlowInEasing))
    }

    Canvas(modifier) {
        val count = 19
        val centreY = size.height / 2f
        // One crest and one trough across the whole width, and a quarter of it at each end, so
        // the line starts and finishes level - a wave that starts mid-rise reads as a chart.
        val amplitude = size.height * 0.30f
        val step = size.width / (count - 1)

        for (i in 0 until count) {
            val along = i / (count - 1f)
            val x = step * i
            val y = centreY - amplitude * sin(along * 2f * PI.toFloat())

            // Lit from the **bottom up**.
            //
            // Left to right, a wave arriving reads as a line being drawn. Bottom to top it reads
            // as water: the trough fills first and the crest comes up out of it, which is the
            // direction a wave actually forms. So the threshold runs on height, not on distance
            // along the curve - the lowest dot is lit at once and the highest is last.
            val heightFrac = if (size.height > 0f) y / size.height else 0f
            val local = ((progress.value - (1f - heightFrac) * 0.62f) / 0.38f).coerceIn(0f, 1f)
            if (local <= 0f) continue

            // Tapers toward the right, the way the icon does.
            val radius = size.minDimension * (0.030f + 0.020f * local) * (1f - 0.30f * along)

            // The light the dot throws, behind it.
            drawCircle(
                color = GlowAccent.copy(alpha = 0.30f * local),
                radius = radius * 2.6f,
                center = Offset(x, y),
            )
            drawCircle(
                color = GlowAccent.copy(alpha = local),
                radius = radius,
                center = Offset(x, y),
            )
        }
    }
}

private fun MaterialThemeShape() = RoundedCornerShape(14.dp)

/** A tappable label, for the two buttons on the two pages. */
private fun Modifier.clickableText(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)