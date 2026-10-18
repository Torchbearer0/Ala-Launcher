/*
 * SPDX-FileCopyrightText: 2026 Victoria Launcher contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package dev.victorialauncher.ui.common

import android.app.Activity
import android.app.WallpaperManager
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
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
            try {
                val wm = WallpaperManager.getInstance(context)
                val drawable = wm.drawable
                android.util.Log.i(
                    "BlurredWallpaper",
                    "drawable=" + (drawable?.let { "${it.intrinsicWidth}x${it.intrinsicHeight}" } ?: "null"),
                )
                if (drawable == null) null else drawable.toBitmap().asImageBitmap()
            } catch (t: Throwable) {
                android.util.Log.i("BlurredWallpaper", "failed: ${t.javaClass.simpleName}: ${t.message}")
                null
            }
        }
    }

    val view = LocalView.current
    val density = LocalDensity.current

    /**
     * The blur itself, done by the **system**, not by us.
     *
     * This is the only way to blur what is behind a transparent launcher window, and the first
     * attempt did not do it at all: `WallpaperManager.getDrawable()` fetches the wallpaper as a
     * drawable, which on Android 13 and later needs `READ_MEDIA_IMAGES` -
     *
     *     SecurityException: Permission android.permission.READ_MEDIA_IMAGES denied
     *
     * - and a launcher has no business holding broad media access to make one page look nice.
     * It is also the wrong shape of answer: it fetches a bitmap of the wallpaper when what is
     * actually wanted is a blur of *everything* behind the window, whatever that happens to be.
     *
     * `Window.setBackgroundBlurRadius` is built for exactly this. The platform renders the layer
     * behind the window once, blurs it, and hands it back - no permission, no decoding, and it
     * costs nothing when the page is not on screen because it is switched off again on dispose.
     *
     * Needs API 31. Below that there is no way to do this and the page keeps its scrim.
     */
    DisposableEffect(view, radius) {
        val window = (view.context as? Activity)?.window
        val px = with(density) { radius.toPx() }
        val applied = window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        if (applied) window?.setBackgroundBlurRadius(px.toInt())
        onDispose { if (applied) window?.setBackgroundBlurRadius(0) }
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