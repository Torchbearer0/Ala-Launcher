// SPDX-License-Identifier: GPL-3.0-or-later
package dev.victorialauncher.widget

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import java.util.Locale

/**
 * The last weather another app sent us, and whether it is still worth drawing.
 *
 * Victoria has no network permission and is not getting one, so there is no fetching here: a
 * weather app that already has the forecast broadcasts it, and this is where what arrived is
 * kept. Nothing asks for a location either — the sender says where it is.
 *
 * Written to disk rather than held in memory because a widget outlives the process that drew
 * it. A launcher gets killed often, and weather that vanishes every time it does is worse than
 * no weather at all.
 */
object ClockWidgetWeather {

    private const val FILE = "clock_widget_weather"
    private const val KEY_TEMP_K = "tempK"
    private const val KEY_CONDITION = "condition"
    private const val KEY_RECEIVED_AT = "receivedAt"

    /**
     * Past this, say nothing rather than something old.
     *
     * A forecast is a claim about now. Breezy sends on its own schedule and after a long doze
     * the last one can be hours stale, which is the one case where a blank row is the honest
     * answer.
     */
    private const val FRESH_FOR_MS = 3 * 60 * 60 * 1000L

    /** Kelvin, and wide enough for anywhere on Earth without accepting a parse gone wrong. */
    private val PLAUSIBLE_K = 150..350

    /** Enough for "Partly cloudy"; past this something is wrong with the sender, not the sky. */
    private const val MAX_CONDITION_CHARS = 40

    data class Reading(val tempK: Int, val condition: String)

    /** Null when nothing has arrived, or when what did is too old to repeat. */
    fun current(context: Context): Reading? {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val tempK = prefs.getInt(KEY_TEMP_K, 0)
        if (tempK !in PLAUSIBLE_K) return null
        val receivedAt = prefs.getLong(KEY_RECEIVED_AT, 0L)
        if (receivedAt <= 0L || System.currentTimeMillis() - receivedAt > FRESH_FOR_MS) return null
        return Reading(tempK, prefs.getString(KEY_CONDITION, null).orEmpty())
    }

    /** Returns false for anything that does not look like a temperature, having stored nothing. */
    fun store(context: Context, tempK: Int, condition: String?): Boolean {
        if (tempK !in PLAUSIBLE_K) return false
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putInt(KEY_TEMP_K, tempK)
            .putString(KEY_CONDITION, condition?.take(MAX_CONDITION_CHARS)?.trim().orEmpty())
            .putLong(KEY_RECEIVED_AT, System.currentTimeMillis())
            .apply()
        return true
    }

    /** The reading on its own, for the row: just how warm it is. */
    fun degrees(reading: Reading, fahrenheit: Boolean): String {
        val celsius = reading.tempK - 273.15
        val value = if (fahrenheit) celsius * 9 / 5 + 32 else celsius
        val unit = if (fahrenheit) "F" else "C"
        return String.format(Locale.getDefault(), "%.0f°%s", value, unit)
    }

    /**
     * Degrees and the condition in words, for anywhere that has room for both.
     *
     * The clock widget itself draws an icon instead of the words (see [glyphFor]) and keeps the
     * condition for the content description, because a glyph nobody can read is not a forecast.
     */
    fun formatted(reading: Reading, fahrenheit: Boolean): String {
        val degrees = degrees(reading, fahrenheit)
        return if (reading.condition.isEmpty()) degrees else "$degrees  ${reading.condition}"
    }

    /**
     * The picture that goes with a condition, if one can be told from the words.
     *
     * The condition is free text written by whichever app chose to send it, so this matches
     * substrings rather than expecting any particular wording. [UNKNOWN] is a real answer and
     * not a failure: a sender is free to send something this does not know, and drawing a
     * confident wrong sky is worse than drawing no sky.
     *
     * Two simplifications worth stating rather than hiding. A sender that reports more than one
     * condition at once ("partly cloudy, light rain later") is drawn as whichever word this
     * finds first, not as the first half of the sentence; parsing that needs a real forecast
     * model, and guessing at one would be worse than taking the first word. And "shower" is
     * matched on its own, so a phrase that uses it for something other than rain would be drawn
     * as rain. Both are recorded here because they are the cases that would otherwise look like
     * bugs in the drawing rather than in the matching.
     *
     * Deliberately free of any resource reference so it can be tested without a device. The
     * glyph-to-drawable step is the provider's, which is the only part that needs an `R`.
     */
    fun glyphFor(condition: String): WeatherGlyph {
        val text = condition.lowercase(Locale.ROOT)
        if (text.isBlank()) return WeatherGlyph.UNKNOWN
        // In this order because the general words are inside the specific ones: a thunderstorm
        // is also rain, sleet is also rain, "mostly cloudy" is also cloudy. Asking the broad
        // question first is how a thunderstorm ends up drawn as a drizzle.
        return when {
            contains(text, "thunder", "lightning", "storm", "thundershower") ->
                WeatherGlyph.THUNDERSTORM
            contains(text, "snow", "sleet", "blizzard", "hail", "freezing") ->
                WeatherGlyph.SNOW
            contains(text, "drizzle") -> WeatherGlyph.DRIZZLE
            contains(text, "rain", "shower") -> WeatherGlyph.RAIN
            contains(text, "fog", "mist", "haze", "smoke", "dust") -> WeatherGlyph.FOG
            contains(text, "partly", "mostly cloud", "few cloud",
                     "scattered", "broken", "intervals") -> WeatherGlyph.PARTLY_CLOUDY
            contains(text, "cloud", "overcast") -> WeatherGlyph.CLOUDY
            contains(text, "clear", "sunny", "fair", "blue sky") -> WeatherGlyph.CLEAR
            else -> WeatherGlyph.UNKNOWN
        }
    }

    private fun contains(text: String, vararg needles: String): Boolean =
        needles.any { text.contains(it) }

    /** What can be drawn. Anything not listed here draws nothing rather than something wrong. */
    enum class WeatherGlyph {
        CLEAR,
        PARTLY_CLOUDY,
        CLOUDY,
        FOG,
        DRIZZLE,
        RAIN,
        SNOW,
        THUNDERSTORM,
        UNKNOWN,
    }

    /** Where Fahrenheit is what people mean by a temperature, so the first draw is not wrong. */
    fun fahrenheitByDefault(): Boolean =
        Locale.getDefault().country in setOf("US", "LR", "MM", "BS", "BZ", "KY", "PW")

    /**
     * Turns the receiver on, or off again once no widget wants weather.
     *
     * It ships disabled. A receiver another app can reach is surface that should not exist
     * while the feature is switched off, and this is the one way to have none at all rather
     * than some that is merely careful.
     */
    fun setReceiverEnabled(context: Context, enabled: Boolean) {
        runCatching {
            context.packageManager.setComponentEnabledSetting(
                ComponentName(context, ClockWidgetWeatherReceiver::class.java),
                if (enabled) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                },
                PackageManager.DONT_KILL_APP,
            )
        }
    }
}
