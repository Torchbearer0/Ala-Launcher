/*
 * SPDX-FileCopyrightText: 2026 Victoria Launcher contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package dev.victorialauncher.ui.common

import android.app.Activity
import android.app.WallpaperManager
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.util.Log
import android.graphics.Bitmap
import android.graphics.Canvas
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

private const val TAG = "BlurredWallpaper"

/** The Activity behind however many [ContextWrapper]s Compose wrapped the context in. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * The layer behind the launcher window, softened, for the widget page.
 *
 * ## Why this is drawn at all
 *
 * The launcher's window is transparent and the wallpaper belongs to the window manager, so there
 * has never been anything in the composition to blur - the only thing a page could put behind
 * itself was a flat dark wash, and a wash is not a blur. A wash darkens; a blur softens. One lets
 * you see exactly what is behind the page and the other lets you see that *something* is, which is
 * what makes a page feel like frosted glass rather than like a sheet of dark plastic laid over it.
 *
 * ## Two ways, because each one alone has failed
 *
 * 1. **The window's own background blur.** `Window.setBackgroundBlurRadius` asks the platform to
 *    blur the layer behind the window. Permission-free, and it blurs whatever is really there.
 *    The first attempt silently did nothing, because it did `(LocalView.current.context as?
 *    Activity)` - and a Compose view's context is normally a `ContextThemeWrapper`, so the cast
 *    returned null every time and the whole thing quietly opted out. Hence [findActivity].
 *
 * 2. **The wallpaper, drawn by us and blurred.** `WallpaperManager.drawable` throws
 *    `SecurityException: READ_MEDIA_IMAGES` on Android 13+, which a launcher should not be asking
 *    for. `fastDrawable` is the API written for launchers and is tried first.
 *
 * Both are attempted because either may be unavailable - a locked secure wallpaper will not hand
 * over a drawable either way - and one of them producing something is enough. What is logged here
 * has been the difference between "the blur is wrong" and "the blur never ran".
 *
 * ## Cost
 *
 * The wallpaper is fetched once and held, so the steady state is a blurred bitmap being redrawn
 * rather than the wallpaper being decoded repeatedly, and the window blur is switched off again on
 * dispose so it costs nothing when the page is not on screen.
 */
@Composable
fun BlurredWallpaper(
    radius: Dp = 30.dp,
    modifier: Modifier = Modifier,
    // **Transparent, and that is the whole point.**
    //
    // The launcher's window is translucent, so the system composites the blurred backdrop into the
    // window's own background layer - and a single opaque fill over the top hides every bit of it.
    // It was black, so the page went from "a sharp wallpaper under a scrim" (what it had always
    // done) to "pure black", which looked exactly like the blur never running. It had run.
    //
    // Nothing is drawn at all when there is no wallpaper to draw; the window behind supplies
    // whatever there is, blurred.
    fallback: Color = Color.Transparent,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current

    // (1) The system blur.
    DisposableEffect(view, radius) {
        val window = context.findActivity()?.window
        val px = with(density) { radius.toPx() }.toInt()
        val applied = window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        if (applied) {
            Log.i(TAG, "window blur applied radius=$px")
            window?.setBackgroundBlurRadius(px)
        } else {
            Log.i(TAG, "window blur NOT applied: window=$window sdk=${Build.VERSION.SDK_INT}")
        }
        onDispose { if (applied) window?.setBackgroundBlurRadius(0) }
    }

    // (2) The wallpaper, blurred by us.
    //
    // The theme sets `windowShowWallpaper=true` with a transparent window background, so the
    // wallpaper is installed as **this window's own background drawable**. That makes it ours to
    // draw with - no `WallpaperManager`, and so no `READ_MEDIA_IMAGES`, which is denied to this
    // package on Android 13+ and which a launcher has no business holding anyway.
    val wallpaper by produceState<ImageBitmap?>(initialValue = null, context) {
        value = withContext(Dispatchers.IO) {
            val decor = context.findActivity()?.window?.decorView
            val bg = decor?.background
            Log.i(TAG, "decor background = " + (bg?.javaClass?.name ?: "null"))
            if (bg == null || decor == null) {
                null
            } else {
                val w = decor.width.takeIf { it > 0 } ?: 1080
                val h = decor.height.takeIf { it > 0 } ?: 2376
                try {
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bg.setBounds(0, 0, w, h)
                    bg.draw(Canvas(bmp))
                    Log.i(TAG, "drew window background into ${w}x$h")
                    bmp.asImageBitmap()
                } catch (t: Throwable) {
                    Log.i(TAG, "background draw failed: ${t.javaClass.simpleName}: ${t.message}")
                    null
                }
            }
        }
    }

    Box(
        modifier.fillMaxSize(),
    ) {
        wallpaper?.let { image ->
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(radius),
            )
        }
    }
}