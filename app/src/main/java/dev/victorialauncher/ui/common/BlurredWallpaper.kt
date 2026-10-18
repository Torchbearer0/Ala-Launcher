/*
 * SPDX-FileCopyrightText: 2026 Victoria Launcher contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package dev.victorialauncher.ui.common

import android.app.WallpaperManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The system wallpaper, blurred, filling whatever it is put behind.
 *
 * ## Why this is drawn at all
 *
 * The launcher's window is transparent and the wallpaper belongs to the window manager, so there
 * has never been anything in the composition to blur - the only thing a page could put behind
 * itself was a flat dark wash, and a wash is not a blur. A wash darkens; a blur softens. One lets
 * you see exactly what is behind the page and the other lets you see that *something* is, which is
 * what makes a page feel like frosted glass rather than like a sheet of dark plastic laid over it.
 *
 * So the wallpaper is fetched and painted here, blurred, and the page's own scrim goes on top of
 * it. This is the one place in the app where that is the right trade: the widget page is a full
 * screen of arbitrary third-party widgets, and a blurred backdrop is the only thing that keeps
 * their colours from fighting the page's own chrome. The app panels and the long-press panel are
 * deliberately *not* doing this - they are overlays on something already there, and blurring what
 * is behind an overlay blurs the thing the overlay was opened from.
 *
 * ## Cost
 *
 * A full-screen blur is a real offscreen pass every time this recomposes. The radius is a
 * deliberate compromise - enough to stop the wallpaper reading as wallpaper, not so much that it
 * turns into flat colour - and the image is fetched once and held, so the steady state is a
 * blurred bitmap being redrawn rather than the wallpaper being fetched and decoded repeatedly.
 * The page is only composed while it is on screen at all.
 *
 * ## If there is no wallpaper
 *
 * Some devices report none, and a locked secure one will not hand over a drawable. Falls back to
 * plain black, which is what the page had before any of this.
 */
@Composable
fun BlurredWallpaper(
    radius: Dp = 30.dp,
    modifier: Modifier = Modifier,
    fallback: Color = Color.Black,
) {
    val context = LocalContext.current

    val wallpaper by produceState<ImageBitmap?>(initialValue = null, context) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val drawable = WallpaperManager.getInstance(context).drawable ?: return@runCatching null
                drawable.toBitmap().asImageBitmap()
            }.getOrNull()
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(fallback),
    ) {
        wallpaper?.let { image ->
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                // The blur is applied to *this* content, which is why the wallpaper has to be
                // painted here rather than left to the window behind.
                modifier = Modifier
                    .fillMaxSize()
                    .blur(radius),
            )
        }
    }
}