// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

import org.json.JSONObject

/**
 * Takes weather from whichever app is already fetching it, over the broadcast Gadgetbridge
 * defined and Breezy Weather sends.
 *
 * This has to be exported, or the app sending it cannot reach us, and a broadcast carries no
 * trustworthy word on who sent it — so anything arriving here is a stranger's input and is
 * treated as such. What that buys an attacker is the wrong temperature on your own home
 * screen, which is the whole of it; there is nothing else here to reach.
 *
 * Two deliberate omissions. `WeatherGz` is ignored, so nothing untrusted is ever decompressed —
 * a few hundred bytes claiming to expand into a great many is a denial of service that costs
 * the sender nothing, and the gzipped extra only ever carried locations we do not draw.
 * `WeatherSecondaryJson` is ignored for the same reason: one widget shows one temperature.
 */
class ClockWidgetWeatherReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_GENERIC_WEATHER) return
        val json = runCatching { intent.getStringExtra(EXTRA_WEATHER_JSON) }.getOrNull() ?: return
        // Read before parsing: a parser handed something enormous is the problem, not the
        // answer it eventually gives.
        if (json.length > MAX_JSON_CHARS) return

        val parsed = runCatching {
            val obj = JSONObject(json)
            // Kelvin, as whole degrees. Gadgetbridge's own WeatherSpec is the shape here.
            obj.optInt("currentTemp", 0) to obj.optString("currentCondition", "")
        }.getOrNull() ?: return

        // The coming days ride along in the same payload, so they cost nothing extra to
        // receive and no second permission to get. Parsed separately and allowed to fail on
        // its own: a sender whose forecast is malformed should still get today's temperature
        // drawn, not lose the whole broadcast.
        val forecast = ClockWidgetWeather.parseForecast(
            runCatching { JSONObject(json).optJSONArray("forecasts")?.toString() }.getOrNull(),
        )

        // The day's condition is drawn as the sender's own wording rather than looked up from its
        // code: the code in that field is the sender's numbering, not the one the Gadgetbridge
        // shape documents, so a table written against the wrong one gives five days of numbers
        // and no sky — which reads as the feature working.
        if (!ClockWidgetWeather.store(context, parsed.first, parsed.second, forecast)) return

        // The widgets are only ever drawn when something asks, and weather arriving is the
        // only thing that knows this one changed.
        runCatching {
            val manager = AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(ClockWidgetProvider.componentName(context))
                ?.forEach { ClockWidgetProvider.render(context, manager, it) }
        }
    }

    private companion object {
        const val ACTION_GENERIC_WEATHER =
            "nodomain.freeyourgadget.gadgetbridge.ACTION_GENERIC_WEATHER"
        const val EXTRA_WEATHER_JSON = "WeatherJson"

        /** One location's forecast is a few hundred bytes; this is room to spare and no more. */
        const val MAX_JSON_CHARS = 64 * 1024
    }
}
